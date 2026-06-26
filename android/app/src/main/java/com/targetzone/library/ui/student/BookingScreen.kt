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
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.CFTheme
import com.cashfree.pg.ui.api.CFDropCheckoutPayment
import com.razorpay.Checkout
import com.targetzone.library.BuildConfig
import com.targetzone.library.MainActivity
import com.targetzone.library.data.model.Plan
import com.targetzone.library.data.model.PaymentOrder
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*
import org.json.JSONObject

@Composable
fun BookingScreen(vm: StudentViewModel, onSuccess: () -> Unit) {
    val membership   by vm.membership.collectAsState()
    val plans        by vm.plans.collectAsState()
    val seats        by vm.seats.collectAsState()
    val selectedSeat by vm.selectedSeat.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val error        by vm.error.collectAsState()
    val bookingDone  by vm.bookingSuccess.collectAsState()

    var step         by remember { mutableIntStateOf(1) }
    var selectedPlan by remember { mutableStateOf<Plan?>(null) }
    var shift        by remember { mutableStateOf("MORNING") }

    val context = LocalContext.current
    val activity = context as? MainActivity

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
    LaunchedEffect(Unit) { vm.loadDashboard(); vm.loadPlans() }
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
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint)) {
                Text(it, color = RedAlert, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            vm.clearError()
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
                        selectedPlan = plan; vm.selectSeat(null); step = 2
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
                            onClick = { shift = s },
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
                        Button(onClick = { step = 3 }, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep)) {
                            Text("Continue", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { step = 1 }) { Text("← Back to Plans", color = TextSub) }
        }

        // Step 3 — summary & pay
        if (step == 3 && selectedPlan != null && selectedSeat != null) {
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Text("Order Summary", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                InfoRow("Plan", selectedPlan!!.name)
                InfoRow("Seat", selectedSeat!!.seatNumber)
                InfoRow("Shift", if (selectedPlan!!.planType == "FULL_DAY") "Full Day" else shift)
                InfoRow("Duration", "30 days")
                InfoRow("Start Date", java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()))
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("₹${selectedPlan!!.price.toInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Amber)
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (isLoading) "Processing…" else "Pay ₹${selectedPlan!!.price.toInt()}",
                enabled = !isLoading,
                onClick = {
                    vm.startPayment(
                        planId = selectedPlan!!.id,
                        seatNumber = selectedSeat!!.seatNumber,
                        shift = if (selectedPlan!!.planType == "FULL_DAY") "FULL_DAY" else shift
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                Text("← Change Seat", color = TextSub)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AmberFaint else CardBg)
            .border(1.dp, if (selected) Amber else DividerColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium)
                    Text(plan.description, color = TextSub, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${plan.price.toInt()}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Amber)
                    Text("/month", color = TextMuted, fontSize = 11.sp)
                }
            }
            if (plan.planType == "FULL_DAY") {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(AmberFaint).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("Most Popular", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun openRazorpay(
    activity: MainActivity?,
    order: PaymentOrder,
    callback: (success: Boolean, paymentId: String?, orderId: String?, signature: String?, error: String?) -> Unit
) {
    activity ?: return
    activity.onPaymentResult = callback
    val checkout = Checkout()
    checkout.setKeyID(order.razorpayKeyId)
    val options = JSONObject().apply {
        put("name", "Target Zone Library")
        put("description", "Library Membership")
        put("order_id", order.orderId)
        put("amount", (order.amount * 100).toLong())
        put("currency", "INR")
        put("theme", JSONObject().put("color", "#F59E0B"))
    }
    checkout.open(activity, options)
}

private fun openCashfree(
    activity: MainActivity?,
    order: PaymentOrder,
    callback: (success: Boolean, orderId: String?, error: String?) -> Unit
) {
    activity ?: return
    activity.onCashfreeResult = callback
    try {
        val env = if (BuildConfig.CASHFREE_ENV == "production")
            CFSession.Environment.PRODUCTION else CFSession.Environment.SANDBOX
        val cfSession = CFSession.CFSessionBuilder()
            .setEnvironment(env)
            .setPaymentSessionID(order.paymentSessionId!!)
            .setOrderId(order.orderId)
            .build()
        val cfTheme = CFTheme.CFThemeBuilder()
            .setPrimaryTextColor("#FFFFFF")
            .setSecondaryTextColor("#AAAAAA")
            .setBackgroundColor("#0D1B4B")
            .setNavigationBarBackgroundColor("#0D1B4B")
            .setNavigationBarTextColor("#FFFFFF")
            .setButtonBackgroundColor("#F59E0B")
            .setButtonTextColor("#000000")
            .build()
        val cfPayment = CFDropCheckoutPayment.CFDropCheckoutPaymentBuilder()
            .setSession(cfSession)
            .setCFNativeCheckoutUITheme(cfTheme)
            .build()
        CFPaymentGatewayService.getInstance().doPayment(activity, cfPayment)
    } catch (e: Exception) {
        callback(false, null, e.message ?: "Failed to open payment")
    }
}

@Composable
private fun StepBar(current: Int, steps: List<String>, onStepClick: (Int) -> Unit) {
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
                    .clickable(enabled = num < current) { onStepClick(num) },
                contentAlignment = Alignment.Center
            ) { Text("$num", color = if (active) NavyDeep else TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(4.dp))
            Text(label, color = if (active) Amber else TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (idx < steps.lastIndex) HorizontalDivider(Modifier.weight(1f), color = if (num < current) Amber else DividerColor)
        }
    }
}
