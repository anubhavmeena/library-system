package com.targetzone.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.Seat
import com.targetzone.library.ui.theme.*

private val ROW_COUNTS = mapOf("A" to 28, "B" to 28, "C" to 28, "D" to 26)
private val ROWS = listOf("A", "B", "C", "D")

@Composable
fun SeatGrid(
    seats: List<Seat>,
    selectedSeatNumber: String?,
    onSeatClick: (Seat) -> Unit
) {
    val seatMap = seats.associateBy { it.seatNumber }

    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .background(NavyMid)
                .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            Text("← ENTRANCE / FRONT →", fontSize = 10.sp, color = TextMuted, letterSpacing = 2.sp)
        }

        Spacer(Modifier.height(12.dp))

        ROWS.forEach { row ->
            val total = ROW_COUNTS[row] ?: return@forEach
            val half  = (total + 1) / 2

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(row, color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(18.dp))

                // Left half
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..half) {
                        val seatNum = "$row$i"
                        SeatButton(seatNum, seatMap[seatNum]?.isBooked == true, selectedSeatNumber == seatNum) {
                            if (seatMap[seatNum]?.isBooked != true)
                                onSeatClick(seatMap[seatNum] ?: Seat(seatNumber = seatNum, row = row))
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.dp).height(24.dp).background(DividerColor))
                Spacer(Modifier.width(12.dp))

                // Right half
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in (half + 1)..total) {
                        val seatNum = "$row$i"
                        SeatButton(seatNum, seatMap[seatNum]?.isBooked == true, selectedSeatNumber == seatNum) {
                            if (seatMap[seatNum]?.isBooked != true)
                                onSeatClick(seatMap[seatNum] ?: Seat(seatNumber = seatNum, row = row))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(CardBg, DividerColor, "Available")
            LegendItem(AmberFaint, Amber, "Selected")
            LegendItem(RedFaint, RedAlert, "Booked")
        }
    }
}

@Composable
private fun SeatButton(label: String, booked: Boolean, selected: Boolean, onClick: () -> Unit) {
    val (bg, border, textColor) = when {
        booked   -> Triple(RedFaint, RedAlert.copy(alpha = 0.3f), RedAlert.copy(alpha = 0.5f))
        selected -> Triple(AmberFaint, Amber, Amber)
        else     -> Triple(CardBg, DividerColor, TextSub)
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = !booked, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label.drop(1), fontSize = 9.sp, color = textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun LegendItem(bg: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(bg).border(1.dp, border, RoundedCornerShape(4.dp)))
        Text(label, fontSize = 11.sp, color = TextSub)
    }
}
