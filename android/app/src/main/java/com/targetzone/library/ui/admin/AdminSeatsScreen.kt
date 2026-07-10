package com.targetzone.library.ui.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.Seat
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.FormDialog
import com.targetzone.library.ui.components.OutlineButton
import com.targetzone.library.ui.components.SeatGrid
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminSeatsScreen(vm: AdminViewModel, onViewStudent: (String) -> Unit = {}) {
    val seats     by vm.adminSeats.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var shift     by remember { mutableStateOf("MORNING") }
    var date      by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var detailSeat by remember { mutableStateOf<Seat?>(null) }
    var expiryView by remember { mutableStateOf(false) }
    val haptics = rememberLibraryHaptics()

    val context = LocalContext.current
    val cal = Calendar.getInstance()
    val datePicker = remember(context) {
        DatePickerDialog(
            context,
            { _, y, m, d -> date = String.format("%04d-%02d-%02d", y, m + 1, d) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    LaunchedEffect(shift, date) { vm.loadAdminSeats(shift, date) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Seat Map", style = MaterialTheme.typography.headlineMedium)
        Text("Live seat availability", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // Shift filter
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MORNING", "EVENING", "FULL_DAY").forEach { s ->
                FilterChip(
                    selected = shift == s,
                    onClick = { haptics.tick(); shift = s },
                    label = { Text(when(s) { "MORNING" -> "Morning"; "EVENING" -> "Evening"; else -> "Full Day" }, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Date picker + expiry view toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineButton(text = date, onClick = { datePicker.show() }, icon = Icons.Default.CalendarToday, height = 40.dp)
            FilterChip(
                selected = expiryView,
                onClick = { haptics.tick(); expiryView = !expiryView },
                label = { Text("📅 Expiry View", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
            )
        }
        Spacer(Modifier.height(12.dp))

        // Stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val booked    = seats.count { it.isBooked }
            val available = seats.count { !it.isBooked }
            AppCard(Modifier.weight(1f)) {
                Text("Booked", color = TextSub, fontSize = 12.sp)
                Text("$booked", color = RedAlert, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            AppCard(Modifier.weight(1f)) {
                Text("Available", color = TextSub, fontSize = 12.sp)
                Text("$available", color = Emerald, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            AppCard(Modifier.weight(1f)) {
                Text("Total", color = TextSub, fontSize = 12.sp)
                Text("${seats.size}", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard(Modifier.fillMaxWidth()) {
            if (isLoading || seats.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            } else {
                SeatGrid(
                    seats = seats,
                    selectedSeatNumber = null,
                    onSeatClick = {},
                    onBookedSeatClick = { seat -> detailSeat = seat },
                    expiryView = expiryView,
                    viewDate = date
                )
            }
        }
        if (!isLoading && seats.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Tap a booked seat to view student details", fontSize = 11.sp, color = TextMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    // Seat detail dialog
    detailSeat?.let { seat ->
        FormDialog(title = "Seat ${seat.seatNumber}", onDismiss = { detailSeat = null }) {
            if (seat.studentId != null) {
                DetailRow("Student", seat.studentName ?: "—", onClick = {
                    haptics.tick()
                    detailSeat = null
                    onViewStudent(seat.studentId)
                })
            } else {
                DetailRow("Student", seat.studentName ?: "—")
            }
            DetailRow("Mobile", seat.studentMobile ?: "—")
            DetailRow("Shift", shift)
            DetailRow("Expires", seat.membershipEnd ?: "—")
            Spacer(Modifier.height(12.dp))
            OutlineButton(text = "Close", onClick = { detailSeat = null }, modifier = Modifier.align(Alignment.End), height = 40.dp)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSub, fontSize = 13.sp)
        Text(
            value,
            color = if (onClick != null) Amber else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = if (onClick != null) TextDecoration.Underline else null
        )
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
}
