package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
    var messageOpen    by remember { mutableStateOf(false) }

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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
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

            OutlinedButton(
                onClick = { messageOpen = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald),
                modifier = Modifier.height(40.dp)
            ) { Text("Message", fontSize = 13.sp) }
        }

        Spacer(Modifier.height(8.dp))
    }

    // Edit profile dialog
    if (editOpen) {
        val plans by vm.plans.collectAsState()
        LaunchedEffect(Unit) { vm.loadPlans() }

        var eName by remember(s.name)              { mutableStateOf(s.name) }
        var eMobile by remember(s.mobile)          { mutableStateOf(s.mobile) }
        var eEmail by remember(s.email)            { mutableStateOf(s.email ?: "") }
        var eAddress by remember(s.address)        { mutableStateOf(s.address ?: "") }
        var eGender by remember(s.gender)          { mutableStateOf(s.gender ?: "") }
        var eDob by remember(s.dateOfBirth)        { mutableStateOf(s.dateOfBirth ?: "") }
        var eJoinedAt by remember(s.joinedAt)      { mutableStateOf(s.joinedAt?.take(10) ?: "") }
        var eSeatNumber by remember(s.seatNumber)  { mutableStateOf(s.seatNumber ?: "") }
        var ePlanId by remember(s.membershipPlanId){ mutableStateOf(s.membershipPlanId ?: "") }

        val genderOptions = listOf("", "Male", "Female", "Other")
        var genderExpanded by remember { mutableStateOf(false) }
        var planExpanded   by remember { mutableStateOf(false) }

        val tfColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber, focusedLabelColor = Amber, cursorColor = Amber,
            unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
        )

        Dialog(onDismissRequest = { editOpen = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text("Edit Profile", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(s.name, color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(eName, { eName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eMobile, { eMobile = it }, label = { Text("Mobile") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eEmail, { eEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eAddress, { eAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, colors = tfColors)
                    Spacer(Modifier.height(10.dp))

                    // Gender dropdown
                    ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                        OutlinedTextField(
                            value = eGender.ifBlank { "—" }, onValueChange = {}, readOnly = true,
                            label = { Text("Gender") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), colors = tfColors
                        )
                        ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }, containerColor = NavyMid) {
                            genderOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt.ifBlank { "—" }, color = TextPrimary) },
                                    onClick = { eGender = opt; genderExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eDob, { eDob = it }, label = { Text("Date of Birth (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(eJoinedAt, { eJoinedAt = it }, label = { Text("Joined Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)

                    if (!s.membershipId.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(eSeatNumber, { eSeatNumber = it.uppercase() }, label = { Text("Seat Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                        Spacer(Modifier.height(10.dp))

                        // Plan dropdown
                        ExposedDropdownMenuBox(expanded = planExpanded, onExpandedChange = { planExpanded = it }) {
                            OutlinedTextField(
                                value = plans.firstOrNull { it.id == ePlanId }?.name ?: "— no change —",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Plan") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(planExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(), colors = tfColors
                            )
                            ExposedDropdownMenu(expanded = planExpanded, onDismissRequest = { planExpanded = false }, containerColor = NavyMid) {
                                DropdownMenuItem(text = { Text("— no change —", color = TextMuted) },
                                    onClick = { ePlanId = s.membershipPlanId ?: ""; planExpanded = false })
                                plans.filter { it.isActive != false }.forEach { plan ->
                                    DropdownMenuItem(text = { Text(plan.name, color = TextPrimary) },
                                        onClick = { ePlanId = plan.id; planExpanded = false })
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editOpen = false }) { Text("Cancel", color = TextSub) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val req = UpdateStudentRequest(
                                    name        = eName.trim(),
                                    mobile      = eMobile.trim().ifBlank { null },
                                    email       = eEmail.trim().ifBlank { null },
                                    address     = eAddress.trim().ifBlank { null },
                                    gender      = eGender.trim().ifBlank { null },
                                    dateOfBirth = eDob.trim().ifBlank { null },
                                    joinedAt    = eJoinedAt.trim().ifBlank { null }
                                )
                                vm.updateStudentProfile(s.id, req) {
                                    val mid = s.membershipId
                                    if (mid != null) {
                                        if (eSeatNumber.isNotBlank() && eSeatNumber != s.seatNumber)
                                            vm.changeSeat(mid, eSeatNumber) {}
                                        if (ePlanId.isNotBlank() && ePlanId != s.membershipPlanId)
                                            vm.updateMembershipPlan(mid, ePlanId) {}
                                    }
                                    editOpen = false
                                }
                            },
                            enabled = eName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }

    // Send message dialog
    if (messageOpen) {
        var msgBody by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { messageOpen = false; msgBody = "" }) {
            Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp)) {
                    Text("Message ${s.name}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Sends via WhatsApp to ${s.mobile}", color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = msgBody,
                        onValueChange = { msgBody = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald, focusedLabelColor = Emerald, cursorColor = Emerald,
                            unfocusedBorderColor = DividerColor, unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { messageOpen = false; msgBody = "" }) { Text("Cancel", color = TextSub) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { vm.sendMessageToStudent(s.id, msgBody.trim()) { messageOpen = false; msgBody = "" } },
                            enabled = msgBody.trim().length >= 5,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = NavyDeep)
                        ) { Text("Send") }
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
