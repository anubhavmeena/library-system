package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.targetzone.library.data.model.UpdateStudentRequest
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var editOpen       by remember { mutableStateOf(false) }

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

        // ── Pending Fees ──────────────────────────────────────────────────────
        if ((s.pendingAmount ?: 0.0) > 0.0) {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Pending Fees")
            Card(
                colors = CardDefaults.cardColors(containerColor = RedFaint),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Outstanding Amount", color = RedAlert, fontSize = 12.sp)
                            Text("₹${s.pendingAmount!!.toInt()}", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Button(
                            onClick = { vm.clearPendingFees(s.id) { vm.loadStudentDetail(studentId) } },
                            colors = ButtonDefaults.buttonColors(containerColor = RedAlert, contentColor = androidx.compose.ui.graphics.Color.White)
                        ) { Text("Mark Cleared", fontSize = 13.sp) }
                    }
                }
            }
        }

        // ── Payment History ───────────────────────────────────────────────────
        val studentPayments by vm.studentPayments.collectAsState()
        LaunchedEffect(studentId) { vm.loadStudentPayments(s.id) }
        if (studentPayments.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Payment History")
            studentPayments.forEach { p ->
                AppCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("₹${p.amount.toInt()}", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            val isCash = p.paymentGateway == "CASH"
                            Text(if (isCash) "Cash" else "Online", color = if (isCash) Amber else Emerald, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            if (p.createdAt != null) Text(p.createdAt.take(10), color = TextMuted, fontSize = 11.sp)
                        }
                        StatusChip(p.status)
                    }
                }
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

            OutlinedButton(
                onClick = { editOpen = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                modifier = Modifier.height(40.dp)
            ) { Text("Edit Profile", fontSize = 13.sp) }

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

    // Edit profile dialog
    if (editOpen) {
        var eName by remember(s.name)        { mutableStateOf(s.name) }
        var eEmail by remember(s.email)      { mutableStateOf(s.email ?: "") }
        var eAddress by remember(s.address)  { mutableStateOf(s.address ?: "") }
        var eGender by remember(s.gender)    { mutableStateOf(s.gender ?: "") }
        var eDob by remember(s.dateOfBirth)  { mutableStateOf(s.dateOfBirth ?: "") }
        val genderOptions = listOf("", "Male", "Female", "Other")
        var genderExpanded by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { editOpen = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text("Edit Profile", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(s.name, color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(eName, { eName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber, cursorColor = Amber, unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eEmail, { eEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber, cursorColor = Amber, unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eAddress, { eAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber, cursorColor = Amber, unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
                    Spacer(Modifier.height(10.dp))

                    // Gender dropdown
                    ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                        OutlinedTextField(
                            value = eGender.ifBlank { "—" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Gender") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber, unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                        ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }, containerColor = NavyMid) {
                            genderOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.ifBlank { "—" }, color = TextPrimary) },
                                    onClick = { eGender = opt; genderExpanded = false }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eDob, { eDob = it }, label = { Text("Date of Birth (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, focusedLabelColor = Amber, cursorColor = Amber, unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editOpen = false }) { Text("Cancel", color = TextSub) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val req = UpdateStudentRequest(
                                    name        = eName.trim(),
                                    email       = eEmail.trim().ifBlank { null },
                                    address     = eAddress.trim().ifBlank { null },
                                    gender      = eGender.trim().ifBlank { null },
                                    dateOfBirth = eDob.trim().ifBlank { null }
                                )
                                vm.updateStudentProfile(s.id, req) { editOpen = false }
                            },
                            enabled = eName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep)
                        ) { Text("Save") }
                    }
                }
            }
        }
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
