package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.targetzone.library.data.model.StudentSummary
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*

@Composable
fun AdminStudentsScreen(vm: AdminViewModel, onViewDetails: (String) -> Unit = {}) {
    val students      by vm.students.collectAsState()
    val totalStudents by vm.totalStudents.collectAsState()
    val seats         by vm.seats.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()

    var search     by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var mStatusFilter by remember { mutableStateOf("") }
    var page       by remember { mutableIntStateOf(0) }

    var changeSeatFor by remember { mutableStateOf<StudentSummary?>(null) }
    var newSeat       by remember { mutableStateOf("") }

    LaunchedEffect(page, statusFilter, mStatusFilter) {
        vm.loadStudents(page, statusFilter.takeIf { it.isNotBlank() }, mStatusFilter.takeIf { it.isNotBlank() })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Students", style = MaterialTheme.typography.headlineMedium)
            if (totalStudents > 0)
                Text("${students.size} of $totalStudents students", color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search by name or mobile…", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSub) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "All", "ACTIVE" to "Active", "INACTIVE" to "Inactive").forEach { (v, l) ->
                    FilterChip(selected = statusFilter == v, onClick = { statusFilter = v; page = 0 }, label = { Text(l, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "All Plans", "ACTIVE" to "Active", "EXPIRED" to "Expired").forEach { (v, l) ->
                    FilterChip(selected = mStatusFilter == v, onClick = { mStatusFilter = v; page = 0 }, label = { Text(l, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber))
                }
            }
        }

        error?.let {
            Text(it, color = RedAlert, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 13.sp)
        }

        if (isLoading && students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(students.filter {
                    search.isBlank() || it.name.contains(search, ignoreCase = true) || it.mobile.contains(search)
                }) { student ->
                    StudentCard(student,
                        onToggleStatus = { vm.toggleStudentStatus(student.id, student.isActive) },
                        onChangeSeat = {
                            changeSeatFor = student
                            student.membershipId?.let { mid -> vm.loadSeats(student.shift ?: "MORNING") }
                        },
                        onViewDetails = { onViewDetails(student.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (page > 0) TextButton(onClick = { page-- }) { Text("← Prev", color = Amber) }
                        else Spacer(Modifier.weight(1f))
                        if (students.size == 20) TextButton(onClick = { page++ }) { Text("Next →", color = Amber) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Change seat dialog
    changeSeatFor?.let { student ->
        Dialog(onDismissRequest = { changeSeatFor = null }) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp)) {
                    Text("Change Seat – ${student.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Current: ${student.seatNumber ?: "—"} · ${student.shift ?: "—"}", color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    SeatGrid(seats = seats, selectedSeatNumber = newSeat.takeIf { it.isNotBlank() }, onSeatClick = { newSeat = it.seatNumber })
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { changeSeatFor = null; newSeat = "" }) { Text("Cancel", color = TextSub) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                student.membershipId?.let { mid -> vm.changeSeat(mid, newSeat) { changeSeatFor = null; newSeat = "" } }
                            },
                            enabled = newSeat.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep)
                        ) { Text("Apply") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCard(student: StudentSummary, onToggleStatus: () -> Unit, onChangeSeat: () -> Unit, onViewDetails: () -> Unit = {}) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(student.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (student.isActive) Emerald else RedAlert, CircleShape)
                    )
                }
                Text(student.mobile, color = TextSub, fontSize = 12.sp)
                if (!student.seatNumber.isNullOrBlank()) {
                    Text("Seat ${student.seatNumber} · ${student.shift}", color = TextMuted, fontSize = 11.sp)
                }
                if (!student.endDate.isNullOrBlank()) {
                    Text("Expires ${student.endDate}", color = TextMuted, fontSize = 11.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                student.membershipStatus?.let { StatusChip(it) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onToggleStatus,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (student.isActive) RedAlert else Emerald),
                border = ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier.height(34.dp)
            ) { Text(if (student.isActive) "Deactivate" else "Activate", fontSize = 12.sp) }
            if (!student.membershipId.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onChangeSeat,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                    modifier = Modifier.height(34.dp)
                ) { Text("Change Seat", fontSize = 12.sp) }
            }
            OutlinedButton(
                onClick = onViewDetails,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                modifier = Modifier.height(34.dp)
            ) { Text("View Details", fontSize = 12.sp) }
        }
    }
}
