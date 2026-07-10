package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.ReminderStudent
import com.targetzone.library.data.model.StudentDetail
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.BannerTone
import com.targetzone.library.ui.components.LoadingScreen
import com.targetzone.library.ui.components.MessageBanner
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.components.SectionHeader
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*

@Composable
fun AdminRemindersScreen(vm: AdminViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val haptics = rememberLibraryHaptics()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text("Reminders", style = MaterialTheme.typography.headlineMedium)
            Text("Expiring memberships & pending fees", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            SecondaryTabRow(
                selectedTabIndex = tab,
                containerColor = NavyMid,
                contentColor = Amber,
                // M3 1.5's TabIndicatorScope requires an explicit offset modifier
                // now — without it the indicator never moves off tab 0, which is
                // why the selected tab looked unhighlighted.
                indicator = { TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tab), color = Amber) }
            ) {
                Tab(selected = tab == 0, onClick = { haptics.tick(); tab = 0 }, text = { Text("Expiring", fontSize = 13.sp) })
                Tab(selected = tab == 1, onClick = { haptics.tick(); tab = 1 }, text = { Text("Pending Fees", fontSize = 13.sp) })
                Tab(selected = tab == 2, onClick = { haptics.tick(); tab = 2 }, text = { Text("Grace Dues", fontSize = 13.sp) })
                Tab(selected = tab == 3, onClick = { haptics.tick(); tab = 3 }, text = { Text("Needs Seat", fontSize = 13.sp) })
            }
        }

        when (tab) {
            0 -> ExpiringTab(vm)
            1 -> PendingFeesTab(vm)
            2 -> GraceDuesTab(vm)
            3 -> NeedsSeatTab(vm)
        }
    }
}

@Composable
private fun ExpiringTab(vm: AdminViewModel) {
    val expiring  by vm.expiring.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()
    val haptics   = rememberLibraryHaptics()
    var withinDays by remember { mutableIntStateOf(7) }
    val selected   = remember { mutableStateListOf<String>() }

    LaunchedEffect(withinDays) { vm.loadExpiring(withinDays) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            success?.let {
                MessageBanner(it, BannerTone.Success)
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(success) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 7).forEach { days ->
                    FilterChip(
                        selected = withinDays == days,
                        onClick = { haptics.tick(); withinDays = days; selected.clear() },
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
            LoadingScreen()
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
                            haptics.tick()
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
    val haptics   = rememberLibraryHaptics()
    val selected  = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { vm.loadPendingFeeStudents() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            success?.let {
                MessageBanner(it, BannerTone.Success)
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(success) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
            }
            PrimaryButton(
                text = when {
                    isLoading -> "Sending…"
                    selected.isNotEmpty() -> "Send to ${selected.size} Selected"
                    else -> "Send to All (${students.size})"
                },
                enabled = students.isNotEmpty() && !isLoading,
                onClick = { vm.sendPendingFeeReminders(if (selected.isNotEmpty()) selected.toList() else emptyList()); selected.clear() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoading && students.isEmpty()) {
            LoadingScreen()
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
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("${students.size} Students")
                        TextButton(onClick = {
                            haptics.tick()
                            if (selected.size == students.size) selected.clear()
                            else { selected.clear(); selected.addAll(students.map { it.id }) }
                        }) { Text(if (selected.size == students.size) "Deselect All" else "Select All", color = Amber, fontSize = 12.sp) }
                    }
                }
                items(students) { s ->
                    PendingFeeCard(s, selected.contains(s.id)) { checked ->
                        if (checked) { if (!selected.contains(s.id)) selected.add(s.id) } else selected.remove(s.id)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GraceDuesTab(vm: AdminViewModel) {
    val students  by vm.graceDuesStudents.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()
    val selected  = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { vm.loadGraceDuesStudents() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Students in grace period who still owe dues to keep their seat", color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            success?.let {
                MessageBanner(it, BannerTone.Success)
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(success) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
            }
            PrimaryButton(
                text = when {
                    isLoading -> "Sending…"
                    selected.isNotEmpty() -> "Send to ${selected.size} Selected"
                    else -> "Send to All (${students.size})"
                },
                enabled = students.isNotEmpty() && !isLoading,
                onClick = { vm.sendGraceDuesReminders(if (selected.isNotEmpty()) selected.toList() else emptyList()); selected.clear() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoading && students.isEmpty()) {
            LoadingScreen()
        } else if (students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No dues to clear", color = TextSub)
                }
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(students) { s ->
                    GraceDuesCard(s, selected.contains(s.id)) { checked ->
                        if (checked) { if (!selected.contains(s.id)) selected.add(s.id) } else selected.remove(s.id)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GraceDuesCard(s: StudentDetail, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
    val haptics = rememberLibraryHaptics()
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSelected, onCheckedChange = { haptics.tick(); onToggle(it) }, colors = CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = TextMuted))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Text(s.mobile, color = TextSub, fontSize = 12.sp)
                // No shift here — the grace-dues endpoint doesn't return one;
                // showing "· null" was the bug.
                if (!s.seatNumber.isNullOrBlank()) Text("Seat ${s.seatNumber}", color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${s.duesAmount?.toInt() ?: 0}", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("dues", color = RedAlert.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NeedsSeatTab(vm: AdminViewModel) {
    val students  by vm.orphanedSeatStudents.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadOrphanedSeatStudents() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Paid, active memberships with no seat actually reserved — usually because the seat-reservation step failed right after payment.",
                color = TextSub, fontSize = 12.sp
            )
        }
        if (isLoading && students.isEmpty()) {
            LoadingScreen()
        } else if (students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Everyone has a seat", color = TextSub)
                }
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(students) { s ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Column {
                            Text(s.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                            Text(s.mobile, color = TextSub, fontSize = 12.sp)
                            if (!s.planName.isNullOrBlank()) Text(s.planName, color = TextMuted, fontSize = 11.sp)
                            if (!s.membershipEnd.isNullOrBlank()) Text("Ends ${s.membershipEnd}", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PendingFeeCard(s: StudentDetail, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
    val haptics = rememberLibraryHaptics()
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSelected, onCheckedChange = { haptics.tick(); onToggle(it) }, colors = CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = TextMuted))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Text(s.mobile, color = TextSub, fontSize = 12.sp)
                // No shift here — the pending-fees endpoint doesn't return one
                // (unlike the full student list); showing "· null" was the bug.
                if (!s.seatNumber.isNullOrBlank()) Text("Seat ${s.seatNumber}", color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${s.pendingAmount?.toInt() ?: 0}", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("pending", color = RedAlert.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ReminderCard(student: ReminderStudent, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
    val urgencyColor = when {
        student.daysRemaining <= 3 -> RedAlert
        student.daysRemaining <= 5 -> Amber
        else -> BlueSoft
    }
    val haptics = rememberLibraryHaptics()
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSelected, onCheckedChange = { haptics.tick(); onToggle(it) }, colors = CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = TextMuted))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Text(student.mobile, color = TextSub, fontSize = 12.sp)
                // No shift here — the expiring-memberships endpoint doesn't
                // return one; showing "· null" was the bug.
                if (!student.seatNumber.isNullOrBlank()) Text("Seat ${student.seatNumber}", color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${student.daysRemaining}d", color = urgencyColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("left", color = urgencyColor.copy(alpha = 0.7f), fontSize = 11.sp)
                Text("Exp: ${student.membershipEnd}", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}
