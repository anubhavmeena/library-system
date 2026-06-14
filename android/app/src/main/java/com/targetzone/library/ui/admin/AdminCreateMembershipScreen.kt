package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.CreateMembershipRequest
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminCreateMembershipScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val plans     by vm.plans.collectAsState()
    val seats     by vm.seats.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()
    val error     by vm.error.collectAsState()

    var userId      by remember { mutableStateOf("") }
    var selectedPlanId by remember { mutableStateOf("") }
    var shift       by remember { mutableStateOf("MORNING") }
    var selectedSeat by remember { mutableStateOf("") }
    var startDate   by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }

    val selectedPlan = plans.find { it.id == selectedPlanId }

    LaunchedEffect(Unit) { vm.loadPlans() }
    LaunchedEffect(shift, selectedPlanId) {
        if (selectedPlanId.isNotBlank()) vm.loadSeats(if (selectedPlan?.planType == "FULL_DAY") "FULL_DAY" else shift)
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(2000); vm.clearMessages(); onBack() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Create Membership", style = MaterialTheme.typography.headlineMedium)
        Text("Manually assign a plan to a student", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        success?.let {
            Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint), modifier = Modifier.fillMaxWidth()) {
                Text("✅  $it", color = Emerald, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Student", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            AppTextField(value = userId, onValueChange = { userId = it }, label = "Student User ID (UUID)")
        }
        Spacer(Modifier.height(12.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Text("Plan", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (plans.isEmpty()) {
                CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
            } else {
                plans.forEach { plan ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = selectedPlanId == plan.id, onClick = { selectedPlanId = plan.id; selectedSeat = "" },
                            colors = RadioButtonDefaults.colors(selectedColor = Amber))
                        Column {
                            Text(plan.name, color = TextPrimary, fontSize = 14.sp)
                            Text("₹${plan.price.toInt()} · ${plan.planType}", color = TextSub, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (selectedPlan?.planType == "HALF_DAY") {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Shift", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MORNING", "EVENING").forEach { s ->
                        FilterChip(selected = shift == s, onClick = { shift = s; selectedSeat = "" },
                            label = { Text(if (s == "MORNING") "Morning" else "Evening", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (selectedPlanId.isNotBlank()) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Select Seat  ${if (selectedSeat.isNotBlank()) "— $selectedSeat selected" else ""}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                SeatGrid(seats = seats, selectedSeatNumber = selectedSeat.takeIf { it.isNotBlank() }, onSeatClick = { selectedSeat = it.seatNumber })
            }
            Spacer(Modifier.height(12.dp))
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Start Date", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            AppTextField(value = startDate, onValueChange = { startDate = it }, label = "YYYY-MM-DD")
        }
        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = if (isLoading) "Creating…" else "Create Membership",
            enabled = userId.isNotBlank() && selectedPlanId.isNotBlank() && selectedSeat.isNotBlank() && startDate.isNotBlank() && !isLoading,
            onClick = {
                vm.createMembership(
                    CreateMembershipRequest(
                        userId = userId.trim(),
                        planId = selectedPlanId,
                        seatNumber = selectedSeat,
                        shift = if (selectedPlan?.planType == "FULL_DAY") "FULL_DAY" else shift,
                        startDate = startDate
                    )
                ) {}
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = TextSub) }
    }
}
