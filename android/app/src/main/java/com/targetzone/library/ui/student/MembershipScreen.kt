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
    val context           = LocalContext.current

    LaunchedEffect(Unit) { vm.loadDashboard(); vm.loadMembershipHistory(); vm.loadMyPayments() }

    val daysLeft = membership?.let {
        max(0L, ((java.text.SimpleDateFormat("yyyy-MM-dd").parse(it.endDate)?.time ?: 0L) - System.currentTimeMillis()) / 86400000L).toInt()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("Membership", style = MaterialTheme.typography.headlineMedium)
            Text("Your current plan & history", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        if (membership != null && membership!!.status == "ACTIVE") {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Text("Active Membership", style = MaterialTheme.typography.titleMedium)
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

                    if (daysLeft != null) {
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
                    OutlinedButton(
                        onClick = { vm.downloadIdCard(context) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.5f)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download ID Card", fontSize = 13.sp)
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
                        Button(onClick = onBookNow, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep)) {
                            Text("Book Now", fontWeight = FontWeight.SemiBold)
                        }
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

        if (myPayments.isNotEmpty()) {
            item { SectionHeader("Payment History") }
            items(myPayments) { p ->
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
