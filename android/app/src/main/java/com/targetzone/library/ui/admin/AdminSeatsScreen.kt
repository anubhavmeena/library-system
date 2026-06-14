package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.SeatGrid
import com.targetzone.library.ui.theme.*

@Composable
fun AdminSeatsScreen(vm: AdminViewModel) {
    val seats     by vm.seats.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var shift by remember { mutableStateOf("MORNING") }

    LaunchedEffect(shift) { vm.loadSeats(shift) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Seat Map", style = MaterialTheme.typography.headlineMedium)
        Text("Live seat availability", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

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
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val booked    = seats.count { it.isBooked }
            val available = seats.count { !it.isBooked }
            AppCard(Modifier.weight(1f)) {
                Text("Booked", color = TextSub, fontSize = 12.sp)
                Text("$booked", color = RedAlert, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            AppCard(Modifier.weight(1f)) {
                Text("Available", color = TextSub, fontSize = 12.sp)
                Text("$available", color = Emerald, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            AppCard(Modifier.weight(1f)) {
                Text("Total", color = TextSub, fontSize = 12.sp)
                Text("${seats.size}", color = Amber, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard(Modifier.fillMaxWidth()) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            } else {
                SeatGrid(seats = seats, selectedSeatNumber = null, onSeatClick = {})
            }
        }
    }
}
