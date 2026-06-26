package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.ReminderStudent
import com.targetzone.library.data.model.StudentDetail
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.components.SectionHeader
import com.targetzone.library.ui.theme.*

@Composable
fun AdminRemindersScreen(vm: AdminViewModel) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text("Reminders", style = MaterialTheme.typography.headlineMedium)
            Text("Expiring memberships & pending fees", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = NavyMid,
                contentColor = Amber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[tab]), color = Amber)
                }
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expiring", fontSize = 13.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Pending Fees", fontSize = 13.sp) })
            }
        }

        when (tab) {
            0 -> ExpiringTab(vm)
            1 -> PendingFeesTab(vm)
        }
    }
}

@Composable
private fun ExpiringTab(vm: AdminViewModel) {
    val expiring  by vm.expiring.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()
    var withinDays by remember { mutableIntStateOf(7) }
    val selected   = remember { mutableStateListOf<String>() }

    LaunchedEffect(withinDays) { vm.loadExpiring(withinDays) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            success?.let {
                Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint)) {
                    Text(it, color = Emerald, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(success) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 7).forEach { days ->
                    FilterChip(
                        selected = withinDays == days,
                        onClick = { withinDays = days; selected.clear() },
                        label = { Text("${days}d", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = when {
                    isLoading -> "Sending…"
                    selected.isNotEmpty() -> "Send to ${selected.size} Selected"
                    else -> "Send to All (${expiring.size})"
                },
                enabled = expiring.isNotEmpty() && !isLoading,
                onClick = { vm.sendReminders(if (selected.isNotEmpty()) selected.toList() else emptyList()); selected.clear() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoading && expiring.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        } else if (expiring.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No expiring memberships", color = TextSub)
                }
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("${expiring.size} Students")
                        TextButton(onClick = {
                            if (selected.size == expiring.size) selected.clear()
                            else { selected.clear(); selected.addAll(expiring.map { it.id }) }
                        }) { Text(if (selected.size == expiring.size) "Deselect All" else "Select All", color = Amber, fontSize = 12.sp) }
                    }
                }
                items(expiring) { student ->
                    ReminderCard(student, selected.contains(student.id)) { checked ->
                        if (checked) { if (!selected.contains(student.id)) selected.add(student.id) }
                        else selected.remove(student.id)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PendingFeesTab(vm: AdminViewModel) {
    val students  by vm.pendingFeeStudents.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()

    LaunchedEffect(Unit) { vm.loadPendingFeeStudents() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            success?.let {
                Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint)) {
                    Text(it, color = Emerald, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(success) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
            }
            PrimaryButton(
                text = if (isLoading) "Sending…" else "Send Reminders to All (${students.size})",
                enabled = students.isNotEmpty() && !isLoading,
                onClick = { vm.sendPendingFeeReminders() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoading && students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        } else if (students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No pending fee students", color = TextSub)
                }
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(students) { s -> PendingFeeCard(s) { vm.clearPendingFees(s.id) {} }; Spacer(Modifier.height(8.dp)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PendingFeeCard(s: StudentDetail, onClear: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Text(s.mobile, color = TextSub, fontSize = 12.sp)
                if (!s.seatNumber.isNullOrBlank()) Text("Seat ${s.seatNumber} · ${s.shift}", color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${s.pendingAmount?.toInt() ?: 0}", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("pending", color = RedAlert.copy(alpha = 0.7f), fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onClear,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald),
                    modifier = Modifier.height(32.dp)
                ) { Text("Clear", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun ReminderCard(student: ReminderStudent, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
    val urgencyColor = when {
        student.daysLeft <= 3 -> RedAlert
        student.daysLeft <= 5 -> Amber
        else -> BlueSoft
    }
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSelected, onCheckedChange = onToggle, colors = CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = TextMuted))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Text(student.mobile, color = TextSub, fontSize = 12.sp)
                if (!student.seatNumber.isNullOrBlank()) Text("Seat ${student.seatNumber} · ${student.shift}", color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${student.daysLeft}d", color = urgencyColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("left", color = urgencyColor.copy(alpha = 0.7f), fontSize = 11.sp)
                Text("Exp: ${student.endDate}", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}
