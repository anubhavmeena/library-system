package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.CreateCashMembershipRequest
import com.targetzone.library.data.model.StudentSummary
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdminCreateMembershipScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val plans     by vm.plans.collectAsState()
    val seats     by vm.seats.collectAsState()
    val eligibleStudents by vm.eligibleStudents.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()
    val error     by vm.error.collectAsState()

    var search        by remember { mutableStateOf("") }
    var selectedStudent by remember { mutableStateOf<StudentSummary?>(null) }
    var selectedPlanId by remember { mutableStateOf("") }
    var shift         by remember { mutableStateOf("MORNING") }
    var selectedSeat  by remember { mutableStateOf("") }
    var startDate     by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }
    var paidAmount    by remember { mutableStateOf("") }
    val haptics = rememberLibraryHaptics()

    val selectedPlan = plans.find { it.id == selectedPlanId }

    // Pending amount is always derived from the selected plan's price minus
    // whatever's been entered as paid — never independently editable, so it
    // can't drift out of sync with either input.
    fun formatAmount(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    val pendingAmount: String = selectedPlan?.let { plan ->
        val paid = paidAmount.toDoubleOrNull() ?: 0.0
        formatAmount((plan.price - paid).coerceAtLeast(0.0))
    } ?: ""

    LaunchedEffect(Unit) { vm.loadPlans(); vm.searchEligibleStudents("") }
    LaunchedEffect(search) { delay(300); vm.searchEligibleStudents(search) }
    LaunchedEffect(shift, selectedPlanId) {
        if (selectedPlanId.isNotBlank()) vm.loadSeats(if (selectedPlan?.planType == "FULL_DAY") "FULL_DAY" else shift)
    }
    // Backend requires paidAmount + pendingAmount to equal the plan price exactly
    // (a hard validation added after a prior incident where a mismatched amount
    // was double-counted as both paid and owed) — defaulting Amount Paid to the
    // full plan price on selection keeps that invariant satisfied out of the box;
    // editing it down for a partial/split payment is what drives pendingAmount up.
    LaunchedEffect(selectedPlanId) {
        selectedPlan?.let { paidAmount = formatAmount(it.price) }
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
            MessageBanner("✅  $it", BannerTone.Success)
            Spacer(Modifier.height(12.dp))
        }
        error?.let {
            MessageBanner("⚠️  $it", BannerTone.Error)
            Spacer(Modifier.height(12.dp))
        }

        // Student search — mirrors web's Find Student step: only students who are
        // NEW (never had a membership) or RELEASED (past membership ended) are
        // eligible for a fresh cash membership; anyone ACTIVE/GRACE/PENDING already
        // has one and would be rejected server-side.
        AppCard(Modifier.fillMaxWidth()) {
            Text("Student", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (selectedStudent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(AmberFaint),
                        contentAlignment = Alignment.Center
                    ) { Text(selectedStudent!!.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Amber, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedStudent!!.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(selectedStudent!!.mobile, color = TextSub, fontSize = 12.sp)
                    }
                    TextButton(onClick = { haptics.tick(); selectedStudent = null }) { Text("Change", color = Amber, fontSize = 12.sp) }
                }
            } else {
                AppTextField(value = search, onValueChange = { search = it }, label = "Search name or mobile…")
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber, modifier = Modifier.size(20.dp))
                    }
                } else if (eligibleStudents.isEmpty()) {
                    Text("No eligible students found", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        eligibleStudents.forEach { st ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { haptics.tick(); selectedStudent = st }
                                    .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(NavyLight),
                                    contentAlignment = Alignment.Center
                                ) { Text(st.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(st.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(st.mobile, color = TextSub, fontSize = 11.sp)
                                }
                                val released = st.displayStatus == "RELEASED"
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background((if (released) RedFaint else EmeraldFaint))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(if (released) "Released" else "New", color = if (released) RedAlert else Emerald, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Text("Plan", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (plans.isEmpty()) {
                CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { plan ->
                        PlanCard(plan = plan, selected = selectedPlanId == plan.id) {
                            selectedPlanId = plan.id
                            selectedSeat = ""
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
                        FilterChip(selected = shift == s, onClick = { haptics.tick(); shift = s; selectedSeat = "" },
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
            Box {
                AppTextField(
                    value = startDate,
                    onValueChange = {},
                    label = "Start Date",
                    enabled = false,
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TextSub) }
                )
                // Disabled text fields don't take taps themselves — an invisible
                // overlay is what actually opens the picker.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { haptics.tick(); showDatePicker = true }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Text("Payment", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Defaults to the full plan price — edit for a partial or split payment. Pending amount is calculated automatically.", color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = paidAmount,
                onValueChange = { paidAmount = it },
                label = "Amount Paid (₹)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(6.dp))
            AppTextField(
                value = pendingAmount,
                onValueChange = {},
                label = "Pending Amount (₹) — auto-calculated",
                enabled = false
            )
        }
        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = if (isLoading) "Creating…" else "Create Membership",
            enabled = selectedStudent != null && selectedPlanId.isNotBlank() && selectedSeat.isNotBlank() &&
                startDate.isNotBlank() && paidAmount.toDoubleOrNull() != null && !isLoading,
            onClick = {
                val student = selectedStudent ?: return@PrimaryButton
                vm.createCashMembership(
                    CreateCashMembershipRequest(
                        studentId    = student.id,
                        planId       = selectedPlanId,
                        seatNumber   = selectedSeat,
                        shift        = if (selectedPlan?.planType == "FULL_DAY") "FULL_DAY" else shift,
                        startDate    = startDate,
                        paidAmount   = paidAmount.toDoubleOrNull(),
                        pendingAmount = pendingAmount.toDoubleOrNull()
                    )
                ) {}
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { haptics.tick(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = TextSub) }
    }

    if (showDatePicker) {
        val utcFormat = remember {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
        val initialMillis = runCatching { utcFormat.parse(startDate)?.time }.getOrNull()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    haptics.tick()
                    // DatePickerState reports UTC midnight for the selected day — must
                    // format with a UTC calendar too, or a local-timezone formatter can
                    // shift the result to the day before/after depending on offset.
                    datePickerState.selectedDateMillis?.let { millis -> startDate = utcFormat.format(Date(millis)) }
                    showDatePicker = false
                }) { Text("OK", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { haptics.tick(); showDatePicker = false }) { Text("Cancel", color = TextSub) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
