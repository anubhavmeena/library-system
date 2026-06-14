package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*

@Composable
fun AdminDashboardScreen(vm: AdminViewModel, onNavigate: (String) -> Unit) {
    val stats     by vm.stats.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadStats() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
                Text("Library overview", color = TextSub, fontSize = 13.sp)
            }
            IconButton(onClick = { vm.loadStats() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Amber)
            }
        }

        if (isLoading && stats == null) {
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(color = Amber, modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Students")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total Students",    stats?.totalStudents?.toString() ?: "—",     accent = BlueSoft,  modifier = Modifier.weight(1f))
            StatCard("Active Students",   stats?.activeStudents?.toString() ?: "—",    accent = Emerald,   modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Active Plans",      stats?.activeMemberships?.toString() ?: "—", accent = Amber,     modifier = Modifier.weight(1f))
            StatCard("Expiring Soon",     stats?.expiringThisWeek?.toString() ?: "—",  accent = RedAlert,  sub = "this week", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Seats")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total",     stats?.totalSeats?.toString() ?: "—",     accent = BlueSoft, modifier = Modifier.weight(1f))
            StatCard("Occupied",  stats?.occupiedSeats?.toString() ?: "—",  accent = RedAlert, modifier = Modifier.weight(1f))
            StatCard("Available", stats?.availableSeats?.toString() ?: "—", accent = Emerald,  modifier = Modifier.weight(1f))
        }

        // Occupancy bar
        val occupancyPct = stats?.let {
            if (it.totalSeats > 0) (it.occupiedSeats * 100 / it.totalSeats) else 0
        } ?: 0
        Spacer(Modifier.height(12.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Occupancy", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("$occupancyPct%", color = Amber, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { occupancyPct / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (occupancyPct > 80) RedAlert else Emerald,
                trackColor = NavyMid
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${stats?.occupiedSeats ?: 0} occupied", color = TextMuted, fontSize = 11.sp)
                Text("${stats?.availableSeats ?: 0} available", color = TextMuted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Revenue")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Today",    "₹${stats?.revenueToday?.toInt() ?: 0}",      accent = Emerald, modifier = Modifier.weight(1f))
            StatCard("This Month", "₹${stats?.revenueThisMonth?.toInt() ?: 0}", accent = Amber, sub = "${stats?.paymentsThisMonth ?: 0} txns", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Quick Links")
        val links = listOf("students" to "👥  Manage Students", "seats" to "⊞  Seat Map", "reminders" to "🔔  Send Reminders", "broadcast" to "📢  Broadcast", "feedback" to "💬  View Feedback", "memberships/new" to "➕  Create Membership")
        links.forEach { (route, label) ->
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onNavigate(route) }) { Text("Go →", color = Amber, fontSize = 13.sp) }
                }
            }
        }
    }
}
