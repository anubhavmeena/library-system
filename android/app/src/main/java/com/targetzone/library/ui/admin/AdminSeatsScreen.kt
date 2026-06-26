package com.targetzone.library.ui.admin

import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.targetzone.library.data.model.Seat
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.SeatGrid
import com.targetzone.library.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminSeatsScreen(vm: AdminViewModel) {
    val seats     by vm.adminSeats.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var shift     by remember { mutableStateOf("MORNING") }
    var date      by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var detailSeat by remember { mutableStateOf<Seat?>(null) }

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
                    onClick = { shift = s },
                    label = { Text(when(s) { "MORNING" -> "Morning"; "EVENING" -> "Evening"; else -> "Full Day" }, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Date picker
        OutlinedButton(
            onClick = { datePicker.show() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
            border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(date, fontSize = 13.sp)
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
                    onBookedSeatClick = { seat -> detailSeat = seat }
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
        Dialog(onDismissRequest = { detailSeat = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp)) {
                    Text("Seat ${seat.seatNumber}", style = MaterialTheme.typography.titleMedium, color = Amber)
                    Spacer(Modifier.height(12.dp))
                    DetailRow("Student", seat.studentName ?: "—")
                    DetailRow("Mobile", seat.studentMobile ?: "—")
                    DetailRow("Shift", shift)
                    DetailRow("Expires", seat.membershipEnd ?: "—")
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { detailSeat = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = Amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSub, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
}
