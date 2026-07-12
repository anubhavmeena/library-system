package com.targetzone.library.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.Seat
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil

// Seat ordering matches the physical library layout (left section reads right→left, right reads left→right)
private val L_TOP    = listOf(13, 11, 9, 7, 5, 3, 1)
private val L_BOTTOM = listOf(14, 12, 10, 8, 6, 4, 2)
private val R_TOP    = listOf(15, 17, 19, 21, 23, 25, 27)
private val R_BOTTOM = listOf(16, 18, 20, 22, 24, 26, 28)
private val ROWS     = listOf("A", "B", "C", "D")
// B8/B18 are physical obstructions; D27/D28 don't exist (row D has only 26 seats)
private val INACTIVE = setOf("B8", "B18", "D27", "D28")

private val SEAT_SIZE  = 28.dp
private val SEAT_GAP   = 4.dp
private val AISLE_W    = 28.dp
private val LABEL_W    = 18.dp
private val SECTION_W  = (7 * 28 + 6 * 4).dp   // 7 seats × 28dp + 6 gaps × 4dp = 220dp
private val SECTION_H  = (28 * 2 + 6).dp        // 2 sub-rows + spacers ≈ 62dp

@Composable
fun SeatGrid(
    seats: List<Seat>,
    selectedSeatNumber: String?,
    onSeatClick: (Seat) -> Unit,
    onBookedSeatClick: ((Seat) -> Unit)? = null,
    // Mirrors the web admin seat map's "Expiry View" toggle (AdminSeatsPage.jsx)
    // — occupied seats show a days-to-expiry badge instead of the seat number.
    expiryView: Boolean = false,
    viewDate: String? = null,
    // The page's own shift filter (MORNING/EVENING/FULL_DAY) — used to mark
    // FULL_DAY occupants with 'X' and other-shift bookings with a green dot
    // when viewing a single shift. Only meaningful in admin view.
    viewingShift: String? = null
) {
    val seatMap   = seats.associateBy { it.seatNumber }
    val adminView = onBookedSeatClick != null

    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        // "ENTRY" label pinned above the aisle column
        Row {
            Spacer(Modifier.width(LABEL_W + SEAT_GAP))
            Spacer(Modifier.width(SECTION_W))
            Box(Modifier.width(AISLE_W), contentAlignment = Alignment.TopCenter) {
                Text("ENTRY", fontSize = 7.sp, color = TextMuted, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(4.dp))

        ROWS.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row, color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(LABEL_W))
                Spacer(Modifier.width(SEAT_GAP))

                // Left section: two sub-rows of 7 seats
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(SEAT_GAP)) {
                        L_TOP.forEach { i -> SeatCell("$row$i", seatMap, selectedSeatNumber, adminView, onSeatClick, onBookedSeatClick, expiryView, viewDate, viewingShift) }
                    }
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(SEAT_GAP)) {
                        L_BOTTOM.forEach { i -> SeatCell("$row$i", seatMap, selectedSeatNumber, adminView, onSeatClick, onBookedSeatClick, expiryView, viewDate, viewingShift) }
                    }
                }

                // Aisle with vertical divider line
                Box(Modifier.width(AISLE_W).height(SECTION_H), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DividerColor.copy(alpha = 0.3f)))
                }

                // Right section: two sub-rows of 7 seats
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(SEAT_GAP)) {
                        R_TOP.forEach { i -> SeatCell("$row$i", seatMap, selectedSeatNumber, adminView, onSeatClick, onBookedSeatClick, expiryView, viewDate, viewingShift) }
                    }
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(SEAT_GAP)) {
                        R_BOTTOM.forEach { i -> SeatCell("$row$i", seatMap, selectedSeatNumber, adminView, onSeatClick, onBookedSeatClick, expiryView, viewDate, viewingShift) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Footer: labels for what's beyond row D
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(LABEL_W + SEAT_GAP))
            listOf("EXIT", "RO / PANTRY", "WASHROOM").forEachIndexed { i, label ->
                if (i > 0) Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    fontSize = 7.sp,
                    color = TextMuted,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .border(0.5.dp, DividerColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .background(NavyMid.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (expiryView) {
                LegendItem(RedDeep, RedAlert.copy(alpha = 0.5f), "Overdue")
                LegendItem(RedFaint, RedAlert, "≤3d")
                LegendItem(OrangeFaint, Orange, "≤7d")
                LegendItem(YellowFaint, YellowWarn, "≤15d")
                LegendItem(EmeraldFaint, Emerald, "Safe")
                LegendItem(CardBg, DividerColor, "Available")
            } else if (adminView) {
                LegendItem(EmeraldFaint, Emerald.copy(alpha = 0.3f), "Available")
                LegendItem(RedFaint, RedAlert, "Male")
                LegendItem(MagentaFaint, Magenta, "Female")
                if (viewingShift != null && viewingShift != "FULL_DAY") {
                    LegendItem(CardBg, DividerColor, "✕ Full-Day")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Emerald))
                        Text("Other shift booked", fontSize = 10.sp, color = TextSub)
                    }
                }
            } else {
                LegendItem(CardBg, DividerColor, "Available")
                LegendItem(AmberFaint, Amber, "Selected")
                LegendItem(RedFaint, RedAlert, "Booked")
            }
        }
    }
}

@Composable
private fun SeatCell(
    seatNum: String,
    seatMap: Map<String, Seat>,
    selectedSeatNumber: String?,
    adminView: Boolean,
    onSeatClick: (Seat) -> Unit,
    onBookedSeatClick: ((Seat) -> Unit)?,
    expiryView: Boolean = false,
    viewDate: String? = null,
    viewingShift: String? = null
) {
    if (seatNum in INACTIVE) {
        Box(
            Modifier
                .size(SEAT_SIZE)
                .clip(RoundedCornerShape(6.dp))
                .background(NavyDeep.copy(alpha = 0.5f))
                .border(0.5.dp, DividerColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
        )
        return
    }
    val seat = seatMap[seatNum] ?: Seat(seatNumber = seatNum, row = seatNum.first().toString())
    SeatButton(
        label              = seatNum,
        booked             = seat.isBooked,
        selected           = selectedSeatNumber == seatNum,
        gender             = seat.studentGender,
        adminView          = adminView,
        bookedClickable    = onBookedSeatClick != null,
        expiryView         = expiryView,
        expiryDays         = if (expiryView && seat.isBooked) daysToExpiry(seat.membershipEnd, viewDate) else null,
        seatShift          = seat.shift,
        otherShiftOccupied = seat.otherShiftOccupied,
        viewingShift       = viewingShift,
        onClick            = { if (seat.isBooked) onBookedSeatClick?.invoke(seat) else onSeatClick(seat) }
    )
}

@Composable
private fun SeatButton(
    label: String,
    booked: Boolean,
    selected: Boolean,
    gender: String? = null,
    adminView: Boolean = false,
    bookedClickable: Boolean = false,
    expiryView: Boolean = false,
    expiryDays: Int? = null,
    seatShift: String? = null,
    otherShiftOccupied: Boolean = false,
    viewingShift: String? = null,
    onClick: () -> Unit
) {
    val isFemale = booked && gender == "Female"
    val (bg, border, textColor) = when {
        expiryDays != null -> expiryColors(expiryDays)
        booked && isFemale -> Triple(MagentaFaint,                   Magenta.copy(alpha = 0.3f),  Magenta.copy(alpha = 0.7f))
        booked             -> Triple(RedFaint,                        RedAlert.copy(alpha = 0.3f), RedAlert.copy(alpha = 0.5f))
        selected           -> Triple(AmberFaint,                      Amber,                       Amber)
        adminView          -> Triple(EmeraldFaint,                    Emerald.copy(alpha = 0.3f),  Emerald.copy(alpha = 0.6f))
        else               -> Triple(CardBg,                          DividerColor,                TextSub)
    }
    val isFullDayOccupant = booked && viewingShift != null && viewingShift != "FULL_DAY" && seatShift == "FULL_DAY"
    val isOtherShiftBooked = !booked && viewingShift != null && viewingShift != "FULL_DAY" && otherShiftOccupied
    val haptics = rememberLibraryHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "seatPressScale")
    Box(
        Modifier
            .size(SEAT_SIZE)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(
                enabled = !booked || bookedClickable,
                interactionSource = interactionSource,
                indication = null, // seat cells are tiny — a full ripple looks noisy at 28dp; scale+haptic carry the feedback
                onClick = { haptics.tick(); onClick() } // enabled=false already blocks taps on a booked, non-admin seat — every onClick that actually fires here is a legitimate action (select, or admin viewing an occupant), not a rejection
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                expiryDays != null -> "$expiryDays"
                expiryView         -> ""
                isFullDayOccupant  -> ""
                else                -> label.drop(1)
            },
            fontSize = 9.sp, color = textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        if (isFullDayOccupant) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidthPx = 1.dp.toPx()
                drawLine(color = border, start = Offset(0f, 0f), end = Offset(size.width, size.height), strokeWidth = strokeWidthPx)
                drawLine(color = border, start = Offset(size.width, 0f), end = Offset(0f, size.height), strokeWidth = strokeWidthPx)
            }
        }
        if (isOtherShiftBooked) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Emerald)
            )
        }
    }
}

// Mirrors AdminSeatsPage.jsx's expiryClasses() thresholds exactly.
private fun expiryColors(days: Int): Triple<Color, Color, Color> = when {
    days < 0   -> Triple(RedDeep,      RedAlert.copy(alpha = 0.5f), RedAlert.copy(alpha = 0.8f))
    days <= 3  -> Triple(RedFaint,     RedAlert.copy(alpha = 0.6f), RedAlert)
    days <= 7  -> Triple(OrangeFaint,  Orange.copy(alpha = 0.6f),   Orange)
    days <= 15 -> Triple(YellowFaint,  YellowWarn.copy(alpha = 0.6f), YellowWarn)
    else       -> Triple(EmeraldFaint, Emerald.copy(alpha = 0.4f),  Emerald)
}

// Mirrors AdminSeatsPage.jsx's daysToExpiry(): ceil((end - viewDate) / 1 day).
// Negative means overdue (membership in GRACE, seat held past its end date).
private fun daysToExpiry(membershipEnd: String?, viewDate: String?): Int? {
    if (membershipEnd.isNullOrBlank() || viewDate.isNullOrBlank()) return null
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val end = fmt.parse(membershipEnd)?.time ?: return null
        val today = fmt.parse(viewDate)?.time ?: return null
        ceil((end - today) / 86400000.0).toInt()
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun LegendItem(bg: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(bg).border(1.dp, border, RoundedCornerShape(4.dp)))
        Text(label, fontSize = 11.sp, color = TextSub)
    }
}
