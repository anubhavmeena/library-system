package com.targetzone.library.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.MainActivity
import com.targetzone.library.data.model.Plan
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*

@Composable
fun BookingScreen(vm: StudentViewModel, onSuccess: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val membership   by vm.membership.collectAsState()
    val plans        by vm.plans.collectAsState()
    val seats        by vm.seats.collectAsState()
    val selectedSeat by vm.selectedSeat.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val error        by vm.error.collectAsState()
    val bookingDone  by vm.bookingSuccess.collectAsState()
    val activeCoupons  by vm.activeCoupons.collectAsState()
    val appliedCoupon  by vm.appliedCoupon.collectAsState()
    val couponError    by vm.couponError.collectAsState()
    val applyingCoupon by vm.applyingCoupon.collectAsState()

    var step         by remember { mutableIntStateOf(1) }
    var selectedPlan by remember { mutableStateOf<Plan?>(null) }
    var shift        by remember { mutableStateOf("MORNING") }
    var couponInput  by remember { mutableStateOf("") }

    val context = LocalContext.current
    val activity = context as? MainActivity
    val haptics = rememberLibraryHaptics()

    // Route to the correct payment gateway based on backend response
    LaunchedEffect(Unit) {
        vm.paymentOrder.collect { order ->
            if (order.gateway == "CASHFREE" && order.paymentSessionId != null) {
                openCashfree(activity, order) { success, orderId, err ->
                    if (success && orderId != null) {
                        // Cashfree verifies server-side; gatewayPaymentId = orderId, no signature
                        vm.verifyPayment(orderId, orderId, "", order.membershipId)
                    } else {
                        vm.setError(err ?: "Payment failed")
                    }
                }
            } else {
                openRazorpay(activity, order) { success, paymentId, orderId, signature, error ->
                    if (success && paymentId != null && orderId != null && signature != null) {
                        vm.verifyPayment(orderId, paymentId, signature, order.membershipId)
                    } else {
                        vm.clearError()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { vm.loadDashboard(); vm.loadPlans(); vm.loadActiveCoupons() }
    LaunchedEffect(bookingDone) { if (bookingDone) { vm.resetBooking(); onSuccess() } }
    LaunchedEffect(selectedPlan, shift) {
        selectedPlan?.let {
            vm.loadSeats(if (it.planType == "HALF_DAY") shift else "FULL_DAY")
        }
    }

    // Active membership gate
    if (membership?.status == "ACTIVE") {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Active Membership", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text("You already have an active plan.", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            InfoRow("Seat", membership?.seatNumber ?: "—")
            InfoRow("Shift", membership?.shift ?: "—")
            InfoRow("Expires", membership?.endDate ?: "—", highlight = true)
        }
        return
    }

    // GRACE gate — the backend rejects create-order while dues are outstanding
    // (PaymentService), so block here too rather than letting the student pick
    // a plan/seat only to hit an error at the payment step.
    if (membership?.status == "GRACE") {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Dues Pending", style = MaterialTheme.typography.headlineSmall, color = RedAlert)
            Text("Clear your outstanding dues before booking a new seat.", color = TextSub, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            InfoRow("Seat", membership?.seatNumber ?: "—")
            InfoRow("Dues", "₹${(membership?.duesAmount ?: 0.0).toInt()}", highlight = true)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = "Clear Dues", onClick = { onNavigate("membership") })
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Book a Seat", style = MaterialTheme.typography.headlineMedium)
        Text("Select plan, seat and pay", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // Step bar
        StepBar(current = step, steps = listOf("Plan", "Seat", "Pay")) { if (it < step) step = it }

        error?.let {
            Spacer(Modifier.height(8.dp))
            MessageBanner(it, BannerTone.Error)
            LaunchedEffect(it) { kotlinx.coroutines.delay(4000); vm.clearError() }
        }

        // Step 1 — choose plan
        if (step == 1) {
            Spacer(Modifier.height(16.dp))
            if (plans.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            } else {
                plans.forEach { plan ->
                    PlanCard(plan, selectedPlan?.id == plan.id) {
                        selectedPlan = plan; vm.selectSeat(null); vm.clearCoupon(); couponInput = ""; step = 2
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // Step 2 — choose seat
        if (step == 2 && selectedPlan != null) {
            Spacer(Modifier.height(12.dp))
            if (selectedPlan!!.planType == "HALF_DAY") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MORNING", "EVENING").forEach { s ->
                        FilterChip(
                            selected = shift == s,
                            onClick = { haptics.tick(); shift = s },
                            label = { Text(if (s == "MORNING") "Morning" else "Evening") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            AppCard(Modifier.fillMaxWidth()) {
                Text("Select Your Seat", style = MaterialTheme.typography.titleMedium)
                Text("${seats.count { !it.isBooked }} seats available", color = TextSub, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                if (isLoading || seats.isEmpty()) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
                else SeatGrid(seats = seats, selectedSeatNumber = selectedSeat?.seatNumber, onSeatClick = { vm.selectSeat(it) })
            }
            if (selectedSeat != null) {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Seat ${selectedSeat?.seatNumber}", color = Amber, fontWeight = FontWeight.SemiBold)
                            Text("Row ${selectedSeat?.row} · ${if (selectedPlan?.planType == "FULL_DAY") "Full Day" else shift}", color = TextSub, fontSize = 12.sp)
                        }
                        PrimaryButton(text = "Continue", onClick = { step = 3 }, modifier = Modifier.height(42.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { haptics.tick(); step = 1 }) { Text("← Back to Plans", color = TextSub) }
        }

        // Step 3 — summary & pay
        if (step == 3 && selectedPlan != null && selectedSeat != null) {
            val discountAmount = appliedCoupon?.let { (selectedPlan!!.price * it.discountPercent / 100.0).let(Math::round) } ?: 0L
            val totalAmount = (selectedPlan!!.price - discountAmount).toInt()

            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Text("Order Summary", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                InfoRow("Plan", selectedPlan!!.name)
                InfoRow("Seat", selectedSeat!!.seatNumber)
                InfoRow("Shift", if (selectedPlan!!.planType == "FULL_DAY") "Full Day" else shift)
                InfoRow("Duration", "30 days")
                InfoRow("Start Date", java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()))
                if (appliedCoupon != null) {
                    InfoRow("Discount (${appliedCoupon!!.code})", "− ₹$discountAmount")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("₹$totalAmount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Amber)
                }
            }

            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                if (appliedCoupon != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Coupon ${appliedCoupon!!.code} applied", color = Emerald, fontSize = 13.sp)
                        TextButton(onClick = { vm.clearCoupon(); couponInput = "" }) { Text("Remove", color = TextSub) }
                    }
                } else {
                    if (activeCoupons.isNotEmpty()) {
                        Text("Available Coupons", color = TextSub, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeCoupons.forEach { c ->
                                AssistChip(
                                    onClick = { couponInput = c.code; vm.applyCoupon(c.code) },
                                    label = { Text("${c.code} · ${c.discountPercent}% off", fontSize = 12.sp) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = Amber, containerColor = AmberFaint)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(
                            value = couponInput,
                            onValueChange = { couponInput = it.uppercase() },
                            label = "Coupon Code",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlineButton(
                            text = if (applyingCoupon) "…" else "Apply",
                            onClick = { vm.applyCoupon(couponInput) },
                            enabled = !applyingCoupon && couponInput.isNotBlank(),
                            height = 50.dp
                        )
                    }
                    couponError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = RedAlert, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (isLoading) "Processing…" else "Pay ₹$totalAmount",
                enabled = !isLoading,
                onClick = {
                    vm.startPayment(
                        planId = selectedPlan!!.id,
                        seatNumber = selectedSeat!!.seatNumber,
                        shift = if (selectedPlan!!.planType == "FULL_DAY") "FULL_DAY" else shift,
                        couponCode = appliedCoupon?.code
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { haptics.tick(); step = 2 }, modifier = Modifier.fillMaxWidth()) {
                Text("← Change Seat", color = TextSub)
            }
        }
    }
}

@Composable
private fun StepBar(current: Int, steps: List<String>, onStepClick: (Int) -> Unit) {
    val haptics = rememberLibraryHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { idx, label ->
            val num = idx + 1
            val active = num <= current
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Amber else CardBg)
                    .border(1.dp, if (active) Amber else DividerColor, RoundedCornerShape(50))
                    .clickable(enabled = num < current) { haptics.tick(); onStepClick(num) },
                contentAlignment = Alignment.Center
            ) { Text("$num", color = if (active) NavyDeep else TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(4.dp))
            Text(label, color = if (active) Amber else TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (idx < steps.lastIndex) HorizontalDivider(Modifier.weight(1f), color = if (num < current) Amber else DividerColor)
        }
    }
}
