package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import com.targetzone.library.data.model.StudentSummary
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*

@Composable
fun AdminStudentsScreen(vm: AdminViewModel, onViewDetails: (String) -> Unit = {}) {
    val students      by vm.students.collectAsState()
    val totalStudents by vm.totalStudents.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()

    var search        by remember { mutableStateOf("") }
    var statusFilter  by remember { mutableStateOf("") }
    var mStatusFilter by remember { mutableStateOf("") }
    var page          by remember { mutableIntStateOf(0) }

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
                    StudentCard(student, onClick = { onViewDetails(student.id) })
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

}

@Composable
private fun StudentCard(student: StudentSummary, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clickable { onClick() }) {
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                student.membershipStatus?.let { StatusChip(it) }
                if ((student.pendingAmount ?: 0.0) > 0.0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RedFaint)
                            .border(1.dp, RedAlert.copy(alpha = 0.3f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text("₹${student.pendingAmount!!.toInt()} due", color = RedAlert, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
