package com.targetzone.library.ui.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.MainActivity
import com.targetzone.library.data.model.Membership
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*
import kotlin.math.max

@Composable
fun MembershipScreen(vm: StudentViewModel, onBookNow: () -> Unit) {
    val membership        by vm.membership.collectAsState()
    val queuedMembership  by vm.queuedMembership.collectAsState()
    val myPayments        by vm.myPayments.collectAsState()
    val history           by vm.membershipHistory.collectAsState()
    val isLoading         by vm.isLoading.collectAsState()
    val error             by vm.error.collectAsState()
    val downloadStatus    by vm.downloadStatus.collectAsState()
    val context           = LocalContext.current
    val activity          = context as? MainActivity

    LaunchedEffect(Unit) { vm.loadDashboard(); vm.loadMembershipHistory(); vm.loadMyPayments() }

    // Route dues payment to the correct gateway, mirroring BookingScreen's flow —
    // Cashfree verifies server-side (gatewayPaymentId = orderId, no signature).
    LaunchedEffect(Unit) {
        vm.duesPaymentOrder.collect { order ->
            if (order.gateway == "CASHFREE" && order.paymentSessionId != null) {
                openCashfree(activity, order) { success, orderId, err ->
                    if (success && orderId != null) {
                        vm.verifyDuesPayment(orderId, orderId, "", order.membershipId)
                    } else {
                        vm.setError(err ?: "Payment failed")
                    }
                }
            } else {
                openRazorpay(activity, order) { success, paymentId, orderId, signature, _ ->
                    if (success && paymentId != null && orderId != null && signature != null) {
                        vm.verifyDuesPayment(orderId, paymentId, signature, order.membershipId)
                    } else {
                        vm.clearError()
                    }
                }
            }
        }
    }

    val rawDaysLeft = membership?.let {
        ((java.text.SimpleDateFormat("yyyy-MM-dd").parse(it.endDate)?.time ?: 0L) - System.currentTimeMillis()) / 86400000L
    }
    val daysLeft = rawDaysLeft?.let { max(0L, it).toInt() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("Membership", style = MaterialTheme.typography.headlineMedium)
            Text("Your current plan & history", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        if (membership != null && (membership!!.status == "ACTIVE" || membership!!.status == "GRACE")) {
            item {
                val isGrace = membership!!.status == "GRACE"
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Text(if (isGrace) "Grace Period" else "Active Membership", style = MaterialTheme.typography.titleMedium)
                            Text(membership!!.planName, color = TextSub, fontSize = 13.sp)
                        }
                        StatusChip(membership!!.status)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = DividerColor)
                    InfoRow("Plan Type", if (membership!!.planType == "FULL_DAY") "Full Day" else "Half Day")
                    InfoRow("Seat Number", membership!!.seatNumber, highlight = true)
                    InfoRow("Shift", membership!!.shift)
                    InfoRow("Start Date", membership!!.startDate)
                    InfoRow("Expires", membership!!.endDate)
                    InfoRow("Amount Paid", "₹${membership!!.amountPaid.toInt()}")

                    if (isGrace) {
                        Spacer(Modifier.height(12.dp))
                        error?.let {
                            MessageBanner(it, BannerTone.Error)
                            Spacer(Modifier.height(8.dp))
                            // Auto-dismiss after a beat instead of clearing inline during
                            // composition — clearing synchronously here raced the Card's
                            // own render, so a real error could disappear before it painted.
                            LaunchedEffect(it) { kotlinx.coroutines.delay(4000); vm.clearError() }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Dues Pending", color = RedAlert, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("₹${(membership!!.duesAmount ?: 0.0).toInt()} to keep this seat", color = RedAlert, fontSize = 11.sp)
                                }
                                PrimaryButton(
                                    text = if (isLoading) "Processing…" else "Pay Now",
                                    onClick = { vm.startDuesPayment() },
                                    enabled = !isLoading,
                                    tone = ButtonTone.Danger,
                                    modifier = Modifier.height(40.dp)
                                )
                            }
                        }
                    } else if (daysLeft != null) {
                        Spacer(Modifier.height(12.dp))
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Days Remaining", color = TextSub, fontSize = 13.sp)
                                Text("$daysLeft days", color = if (daysLeft <= 5) Amber else Emerald, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (daysLeft / 30f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (daysLeft <= 5) Amber else Emerald,
                                trackColor = CardBg
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlineButton(
                        text = "Download ID Card",
                        onClick = { vm.downloadIdCard(context) },
                        icon = Icons.Default.Download,
                        height = 40.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    downloadStatus?.let { status ->
                        val failed = status.startsWith("Download failed") || status.startsWith("Couldn't") || status.startsWith("Not signed in")
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = if (failed) RedAlert else Emerald, fontSize = 12.sp)
                        LaunchedEffect(status) { kotlinx.coroutines.delay(4000); vm.clearDownloadStatus() }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("📋", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No Active Membership", style = MaterialTheme.typography.titleMedium)
                        Text("Purchase a plan to access the library", color = TextSub, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(text = "Book Now", onClick = onBookNow, modifier = Modifier.height(42.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        queuedMembership?.let { q ->
            item {
                SectionHeader("Upcoming Plan")
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(q.planName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Starts ${q.startDate}", color = TextSub, fontSize = 12.sp)
                        }
                        StatusChip("PENDING")
                    }
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Seat", q.seatNumber)
                    InfoRow("Shift", q.shift)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        val successPayments = myPayments.filter { it.status == "SUCCESS" }
        if (successPayments.isNotEmpty()) {
            item { SectionHeader("Payment History") }
            items(successPayments) { p ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("₹${p.amount.toInt()}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            if (!p.paymentGateway.isNullOrBlank()) Text(p.paymentGateway, color = TextMuted, fontSize = 11.sp)
                            if (!p.createdAt.isNullOrBlank()) Text(p.createdAt.take(10), color = TextMuted, fontSize = 11.sp)
                        }
                        StatusChip(p.status)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (history.isNotEmpty()) {
            item { SectionHeader("Membership History") }
            items(history) { m -> MembershipHistoryCard(m); Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MembershipHistoryCard(m: Membership) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.planName, fontWeight = FontWeight.Medium, color = TextPrimary, fontSize = 14.sp)
                Text("Seat ${m.seatNumber} · ${m.shift}", color = TextSub, fontSize = 12.sp)
                Text("${m.startDate}  →  ${m.endDate}", color = TextMuted, fontSize = 11.sp)
            }
            StatusChip(m.status)
        }
    }
}
