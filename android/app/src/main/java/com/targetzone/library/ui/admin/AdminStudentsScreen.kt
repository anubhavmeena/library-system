package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.targetzone.library.data.model.StudentSummary
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil

// Single membership-status filter mirroring the web admin students page
// (frontend/src/pages/admin/AdminStudentsPage.jsx) exactly. The backend's
// displayStatus never returns the literal "EXPIRED" — an overdue-grace
// student is "GRACE_OVERDUE" (see resolve_display_status in
// rust-backend/src/services/membership.rs). Sending "EXPIRED" as
// membershipStatus hits the backend's unmatched `_ => {}` filter arm, which
// applies no WHERE clause at all — that's why "Expired" was showing everyone.
private val MEMBERSHIP_FILTERS = listOf(
    "" to "All", "NEW" to "New", "PAID" to "Paid", "PENDING" to "Pending",
    "GRACE" to "Grace", "GRACE_OVERDUE" to "Expired", "RELEASED" to "Released"
)

@Composable
fun AdminStudentsScreen(vm: AdminViewModel, onViewDetails: (String) -> Unit = {}) {
    val students      by vm.students.collectAsState()
    val totalStudents by vm.totalStudents.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()

    var search           by remember { mutableStateOf("") }
    var debouncedSearch  by remember { mutableStateOf("") }
    var membershipFilter by remember { mutableStateOf("") }
    var page             by remember { mutableIntStateOf(0) }
    val haptics = rememberLibraryHaptics()

    // Debounced live search, matching the web page's 300ms debounce — the
    // search box previously only filtered the already-loaded page client-side
    // instead of querying the backend, so it silently missed every result
    // outside the current 20-row page.
    LaunchedEffect(search) {
        delay(300)
        page = 0
        debouncedSearch = search
    }

    LaunchedEffect(page, membershipFilter, debouncedSearch) {
        vm.loadStudents(page, membershipStatus = membershipFilter.takeIf { it.isNotBlank() }, search = debouncedSearch.takeIf { it.isNotBlank() })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Students", style = MaterialTheme.typography.headlineMedium)
                    Text("${students.size} of $totalStudents students", color = TextSub, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    haptics.tick()
                    vm.loadStudents(page, membershipStatus = membershipFilter.takeIf { it.isNotBlank() }, search = debouncedSearch.takeIf { it.isNotBlank() })
                }) { Text("↻ Refresh", color = TextSub, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search by name or mobile…", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSub) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MEMBERSHIP_FILTERS) { (v, l) ->
                    FilterChip(selected = membershipFilter == v, onClick = { haptics.tick(); membershipFilter = v; page = 0 }, label = { Text(l, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber))
                }
            }
        }

        error?.let {
            Text(it, color = RedAlert, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 13.sp)
        }

        if (isLoading && students.isEmpty()) {
            LoadingScreen()
        } else if (students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No students found", color = TextMuted) }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(students) { student ->
                    StudentCard(student, onClick = { onViewDetails(student.id) })
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (page > 0) TextButton(onClick = { haptics.tick(); page-- }) { Text("← Prev", color = Amber) }
                        else Spacer(Modifier.weight(1f))
                        if (students.size == 20) TextButton(onClick = { haptics.tick(); page++ }) { Text("Next →", color = Amber) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// Mirrors the web row's status badge colors exactly (STATUS_BADGE_CLASSES in
// AdminStudentsPage.jsx) — the shared StatusChip only maps 3 coarse colors
// (ACTIVE/PENDING/EXPIRED) and would collapse NEW/GRACE/RELEASED into gray.
private fun displayStatusColor(status: String?): Color = when (status) {
    "NEW"           -> BlueSoft
    "PAID"          -> Emerald
    "PENDING"       -> YellowWarn
    "GRACE"         -> Orange
    "GRACE_OVERDUE" -> RedAlert
    "RELEASED"      -> RedDeep
    else            -> TextMuted
}

private fun shiftLabel(shift: String?): String = when (shift) {
    "MORNING"  -> "Morning"
    "EVENING"  -> "Evening"
    "FULL_DAY" -> "Full Day"
    else       -> "—"
}

private fun parseDate(s: String?): java.util.Date? {
    if (s.isNullOrBlank()) return null
    return try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) } catch (e: Exception) { null }
}

@Composable
private fun StudentCard(student: StudentSummary, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(NavyLight),
                contentAlignment = Alignment.Center
            ) {
                if (!student.photoUrl.isNullOrBlank()) {
                    // Backend returns a bare "/uploads/..." path, not an absolute
                    // URL — Coil can't resolve that without a host prefix.
                    val fullUrl = student.photoUrl.let { if (it.startsWith("http")) it else "https://targetzone.co.in$it" }
                    AsyncImage(model = fullUrl, contentDescription = student.name, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(student.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    student.displayStatus?.let { status ->
                        val color = displayStatusColor(status)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(color.copy(alpha = 0.15f))
                                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) { Text(status, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                student.joinedAt?.takeIf { it.length >= 10 }?.let {
                    Text("Joined ${it.substring(0, 10)}", color = TextMuted, fontSize = 10.sp)
                }
                Text(student.mobile + (student.email?.let { " · $it" } ?: ""), color = TextSub, fontSize = 12.sp)
                if (!student.seatNumber.isNullOrBlank()) {
                    Text("Seat ${student.seatNumber} · ${shiftLabel(student.shift)}", color = Amber, fontSize = 11.sp)
                }
                MembershipLine(student)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    student.paymentMode?.let { mode ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(IndigoFaint)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) { Text(if (mode == "CASH") "💵 Cash" else "💳 Online", color = Indigo, fontSize = 10.sp) }
                    }
                    if ((student.pendingAmount ?: 0.0) > 0.0) {
                        Text("Pending ₹${student.pendingAmount!!.toInt()}", color = RedAlert, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if ((student.duesAmount ?: 0.0) > 0.0) {
                        Text("Dues ₹${student.duesAmount!!.toInt()}", color = RedAlert, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// Mirrors AdminStudentsPage.jsx's membership column exactly: GRACE shows
// days-overdue in red (recomputed locally since daysRemaining isn't reliable
// once a membership is past its end date), everything else uses the standard
// 3-tier days-left coloring.
@Composable
private fun MembershipLine(s: StudentSummary) {
    val end = parseDate(s.membershipEnd)
    when {
        s.membershipStatus == "GRACE" && end != null -> {
            val overdue = maxOf(0, ceil((System.currentTimeMillis() - end.time) / 86400000.0).toInt())
            Column {
                Text("Expires ${s.membershipEnd}", color = TextSub, fontSize = 11.sp)
                Text(
                    "${overdue}d overdue — ${if (s.displayStatus == "GRACE_OVERDUE") "expired" else "grace"}",
                    color = RedAlert, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        s.membershipEnd != null -> {
            val color = if (s.daysRemaining <= 3) RedAlert else if (s.daysRemaining <= 7) Amber else Emerald
            Column {
                Text("Expires ${s.membershipEnd}", color = TextSub, fontSize = 11.sp)
                Text("${s.daysRemaining} days left", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        else -> Text("No plan", color = TextMuted, fontSize = 11.sp)
    }
}
