package com.targetzone.library.ui.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.User
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.components.StatCard
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import kotlin.math.max

@Composable
fun DashboardScreen(vm: StudentViewModel, user: User?, onNavigate: (String) -> Unit) {
    val membership by vm.membership.collectAsState()
    val haptics = rememberLibraryHaptics()

    LaunchedEffect(Unit) { vm.loadDashboard() }

    // Raw (possibly negative) days left drives color/urgency; the displayed
    // number is floored at 0 so a GRACE student doesn't see a confusing "-3".
    val rawDaysLeft = membership?.let {
        ((java.text.SimpleDateFormat("yyyy-MM-dd").parse(it.endDate)?.time ?: 0L) - System.currentTimeMillis()) / 86400000L
    }
    val daysLeft = rawDaysLeft?.let { max(0, it).toInt() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Hello, ${user?.name?.split(" ")?.firstOrNull() ?: "Student"} 👋", style = MaterialTheme.typography.headlineMedium, color = Amber)
        Text("Welcome to Target Zone Library", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        // Stats grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Plan",
                value = when(membership?.planType) { "FULL_DAY" -> "Full Day"; "HALF_DAY" -> "Half Day"; else -> "—" },
                sub = membership?.let { "₹${it.amountPaid.toInt()}" } ?: "No active plan",
                accent = Amber, modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Seat",
                value = membership?.seatNumber ?: "—",
                sub = membership?.shift ?: "—",
                accent = BlueSoft, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Days Left",
                value = if (rawDaysLeft != null && rawDaysLeft < 0) "Overdue" else daysLeft?.toString() ?: "—",
                sub = membership?.let { "Expires ${it.endDate}" } ?: "No membership",
                accent = when {
                    rawDaysLeft != null && rawDaysLeft < 0 -> RedAlert
                    daysLeft != null && daysLeft <= 5 -> Amber
                    else -> Emerald
                },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Status",
                value = membership?.status ?: "Inactive",
                sub = if (membership != null) "Active membership" else "Get a plan",
                accent = when (membership?.status) {
                    "ACTIVE" -> Emerald
                    "GRACE"  -> RedAlert
                    else     -> Amber
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Dues warning — GRACE means the student is holding the seat but owes
        // money to keep it; shown ahead of the plain expiry-soon warning below.
        if (membership?.status == "GRACE") {
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dues Pending", color = RedAlert, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            membership?.duesAmount?.let { "₹${it.toInt()} due to keep seat ${membership?.seatNumber}" }
                                ?: "Clear your dues to keep seat ${membership?.seatNumber}",
                            color = TextSub, fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { haptics.tick(); onNavigate("membership") }) { Text("Clear Dues", color = RedAlert, fontSize = 12.sp) }
                }
            }
        }

        // Expiry warning
        if (membership?.status == "ACTIVE" && daysLeft != null && daysLeft in 1..7) {
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Expiring in $daysLeft day${if (daysLeft == 1) "" else "s"}!", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Seat ${membership?.seatNumber} — renew soon", color = TextSub, fontSize = 12.sp)
                    }
                    TextButton(onClick = { haptics.tick(); onNavigate("booking") }) { Text("Renew", color = Amber, fontSize = 12.sp) }
                }
            }
        }

        // No membership banner
        if (membership == null) {
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Text("📚", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No Active Membership", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Book a seat to get started", color = TextSub, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "Book a Seat", onClick = { onNavigate("booking") }, modifier = Modifier.height(42.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("QUICK ACTIONS", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))

        val actions = listOf(
            Triple(Icons.Default.EventSeat, "Book Seat", "booking"),
            Triple(Icons.Default.CardMembership, "Membership", "membership"),
            Triple(Icons.Default.Person, "Profile", "profile"),
            Triple(Icons.Default.Info, "Facilities", "facilities"),
            Triple(Icons.Default.Feedback, "Feedback", "feedback"),
        )
        actions.forEach { (icon, label, route) ->
            QuickActionRow(icon, label) { onNavigate(route) }
        }

        Spacer(Modifier.height(20.dp))
        Text("LIBRARY HOURS", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        AppCard(Modifier.fillMaxWidth()) {
            listOf("🌅 Morning" to "6:00 AM – 2:00 PM", "🌆 Evening" to "2:00 PM – 10:00 PM", "🌟 Full Day" to "6:00 AM – 10:00 PM").forEach { (shift, time) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(shift, color = TextPrimary, fontSize = 14.sp)
                    Text(time, color = TextSub, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    // Whole row is now tappable (was previously only the trailing chevron
    // IconButton) — a bigger, more forgiving tap target is a straightforward
    // modernization win here.
    AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = label, tint = Amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = TextSub)
        }
    }
}
