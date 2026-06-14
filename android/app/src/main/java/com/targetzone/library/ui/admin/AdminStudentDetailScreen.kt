package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.targetzone.library.data.model.StudentDetail
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*

@Composable
fun AdminStudentDetailScreen(
    vm: AdminViewModel,
    studentId: String,
    onBack: () -> Unit
) {
    val student   by vm.selectedStudent.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.error.collectAsState()

    val seats by vm.seats.collectAsState()
    var changeSeatOpen by remember { mutableStateOf(false) }
    var newSeat        by remember { mutableStateOf("") }

    LaunchedEffect(studentId) { vm.loadStudentDetail(studentId) }

    if (isLoading && student == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Amber)
        }
        return
    }

    error?.let {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ErrorMessage(it) { vm.loadStudentDetail(studentId) }
        }
        return
    }

    val s = student ?: return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        AppCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Avatar
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AmberFaint)
                        .border(2.dp, Amber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!s.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = s.photoUrl,
                            contentDescription = "Photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            s.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Amber
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp)
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (s.isActive) Emerald else RedAlert, CircleShape)
                        )
                    }
                    Text(s.mobile, color = TextSub, fontSize = 13.sp)
                    if (!s.email.isNullOrBlank()) Text(s.email, color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Profile ───────────────────────────────────────────────────────────
        SectionHeader("Profile")
        AppCard(Modifier.fillMaxWidth()) {
            InfoRow("Gender",   s.gender?.replaceFirstChar { it.titlecase() } ?: "—")
            InfoRow("Date of Birth", s.dateOfBirth ?: "—")
            InfoRow("Address", s.address ?: "—")
            InfoRow("Joined",  s.joinedAt?.take(10) ?: "—")
            InfoRow("Aadhaar", if (!s.aadhaarUrl.isNullOrBlank()) "Uploaded ✓" else "Not uploaded")
        }

        Spacer(Modifier.height(12.dp))

        // ── Membership ────────────────────────────────────────────────────────
        SectionHeader("Membership")
        if (!s.membershipId.isNullOrBlank()) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.planName ?: "—", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    s.membershipStatus?.let { StatusChip(it) }
                }
                Spacer(Modifier.height(4.dp))
                InfoRow("Plan Type", s.planType?.replace("_", " ")?.replaceFirstChar { it.titlecase() } ?: "—")
                InfoRow("Seat",      "${s.seatNumber ?: "—"} · ${s.shift ?: "—"}")
                InfoRow("Start",     s.membershipStart ?: "—")
                InfoRow("Expires",   s.membershipEnd ?: "—")
                InfoRow("Days Left", "${s.daysRemaining} days", highlight = s.daysRemaining <= 7)
                InfoRow("Payment",   s.paymentMode ?: "—")
            }
        } else {
            AppCard(Modifier.fillMaxWidth()) {
                Text("No active membership", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Actions ───────────────────────────────────────────────────────────
        SectionHeader("Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.toggleStudentStatus(s.id, s.isActive) { vm.loadStudentDetail(studentId) } },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (s.isActive) RedAlert else Emerald),
                modifier = Modifier.height(40.dp)
            ) { Text(if (s.isActive) "Deactivate" else "Activate", fontSize = 13.sp) }

            if (!s.membershipId.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        vm.loadSeats(s.shift ?: "MORNING")
                        changeSeatOpen = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                    modifier = Modifier.height(40.dp)
                ) { Text("Change Seat", fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // Change seat dialog
    if (changeSeatOpen) {
        Dialog(onDismissRequest = { changeSeatOpen = false; newSeat = "" }) {
            Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp)) {
                    Text("Change Seat – ${s.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Current: ${s.seatNumber ?: "—"} · ${s.shift ?: "—"}", color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    SeatGrid(seats = seats, selectedSeatNumber = newSeat.takeIf { it.isNotBlank() }, onSeatClick = { newSeat = it.seatNumber })
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { changeSeatOpen = false; newSeat = "" }) { Text("Cancel", color = TextSub) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                s.membershipId?.let { mid ->
                                    vm.changeSeat(mid, newSeat) { changeSeatOpen = false; newSeat = "" }
                                }
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
