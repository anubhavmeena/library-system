package com.targetzone.library.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.StudentSummary
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.ButtonTone
import com.targetzone.library.ui.components.ConfirmDialog
import com.targetzone.library.ui.components.OutlineButton
import com.targetzone.library.ui.components.SeatGrid
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import kotlinx.coroutines.delay

// Mirrors frontend/src/components/admin/StudentActionsMenu.jsx: a grouped,
// drill-down Actions menu shared by the admin Students list (one instance
// per row) and the student's own detail page (one instance in the header).
// Takes plain fields instead of StudentSummary/StudentDetail directly so it
// works against either model.
@Composable
fun StudentActionsMenu(
    vm: AdminViewModel,
    studentId: String,
    name: String,
    mobile: String,
    membershipId: String?,
    membershipStatus: String?,
    displayStatus: String?,
    shift: String?,
    pendingAmount: Double?,
    duesAmount: Double?,
    onMutated: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLibraryHaptics()

    var open by remember { mutableStateOf(false) }
    // Groups expand in place (accordion-style) instead of drilling into a
    // replacement screen, so there's no "‹ Back" step — tapping a group
    // header again (or any item) collapses/closes it.
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    var changeSeatOpen by remember { mutableStateOf(false) }
    var newSeat by remember { mutableStateOf("") }
    var exchangeSeatOpen by remember { mutableStateOf(false) }
    var exchangeSearch by remember { mutableStateOf("") }
    var exchangeTarget by remember { mutableStateOf<StudentSummary?>(null) }
    var deleteOpen by remember { mutableStateOf(false) }
    var clearDuesOpen by remember { mutableStateOf(false) }
    var clearDuesInput by remember { mutableStateOf("") }
    var clearFeesOpen by remember { mutableStateOf(false) }
    var clearFeesInput by remember { mutableStateOf("") }
    var changeStatusOpen by remember { mutableStateOf(false) }
    var changeStatusTarget by remember { mutableStateOf("PENDING") }
    var pendingInput by remember { mutableStateOf("") }
    var msgOpen by remember { mutableStateOf(false) }
    var msgText by remember { mutableStateOf("") }

    val seats by vm.seats.collectAsState()
    val swapCandidates by vm.swapCandidates.collectAsState()

    val canRenew = membershipId != null && displayStatus == "PAID"
    val canChangeSeat = membershipId != null && membershipStatus == "ACTIVE"
    val canExchangeSeat = membershipId != null && membershipStatus == "ACTIVE"
    val canReleaseSeat = membershipId != null && (membershipStatus == "ACTIVE" || membershipStatus == "GRACE")
    val canClearDues = membershipId != null && membershipStatus == "GRACE"
    val canClearFees = (pendingAmount ?: 0.0) > 0.0
    val canChangeStatus = membershipId != null && membershipStatus == "ACTIVE"
    val showSeatGroup = canRenew || canChangeSeat || canExchangeSeat || canReleaseSeat
    val showBillingGroup = canClearDues || canClearFees

    LaunchedEffect(exchangeSearch, exchangeSeatOpen) {
        if (exchangeSeatOpen) {
            delay(300)
            vm.searchSwapCandidates(exchangeSearch, studentId)
        }
    }

    fun closeMenu() { open = false; expandedGroups = emptySet() }
    // Menu-item taps that just open a dialog are a plain tick(); items that
    // fire a real mutation with no further confirmation step (Renew Seat,
    // Release Seat) carry their own confirm()/reject() below.
    fun tickAnd(action: () -> Unit): () -> Unit = { haptics.tick(); action() }
    fun toggleGroup(key: String) {
        haptics.tick()
        expandedGroups = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
    }

    Box {
        OutlineButton(
            text = "Actions ▾",
            onClick = { open = true; expandedGroups = emptySet() },
            tone = ButtonTone.Neutral,
            height = 36.dp,
            modifier = modifier
        )

        DropdownMenu(expanded = open, onDismissRequest = { closeMenu() }, containerColor = NavyMid) {
            if (showSeatGroup) {
                DropdownMenuItem(
                    text = { Text(if ("seat" in expandedGroups) "Seat ▾" else "Seat ▸") },
                    onClick = { toggleGroup("seat") }
                )
                AnimatedVisibility(visible = "seat" in expandedGroups, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        if (canRenew) DropdownMenuItem(
                            text = { Text("Renew Seat", color = BlueSoft, modifier = Modifier.padding(start = 12.dp)) },
                            onClick = {
                                haptics.confirm()
                                membershipId?.let { vm.renewSeat(it) { onMutated() } }
                                closeMenu()
                            }
                        )
                        if (canChangeSeat) DropdownMenuItem(
                            text = { Text("Change Seat", color = Indigo, modifier = Modifier.padding(start = 12.dp)) },
                            onClick = tickAnd {
                                vm.loadSeats(shift ?: "MORNING")
                                changeSeatOpen = true
                                closeMenu()
                            }
                        )
                        if (canExchangeSeat) DropdownMenuItem(
                            text = { Text("Exchange Seat", color = Indigo, modifier = Modifier.padding(start = 12.dp)) },
                            onClick = tickAnd {
                                exchangeSearch = ""
                                exchangeTarget = null
                                vm.searchSwapCandidates("", studentId)
                                exchangeSeatOpen = true
                                closeMenu()
                            }
                        )
                        if (canReleaseSeat) DropdownMenuItem(
                            text = { Text("Release Seat", color = RedAlert, modifier = Modifier.padding(start = 12.dp)) },
                            onClick = {
                                haptics.reject()
                                membershipId?.let { vm.releaseSeat(it, false) { onMutated() } }
                                closeMenu()
                            }
                        )
                    }
                }
            }
            if (showBillingGroup) {
                DropdownMenuItem(
                    text = { Text(if ("billing" in expandedGroups) "Billing ▾" else "Billing ▸") },
                    onClick = { toggleGroup("billing") }
                )
                AnimatedVisibility(visible = "billing" in expandedGroups, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        if (canClearDues) DropdownMenuItem(
                            text = { Text("Clear Dues", color = Emerald, modifier = Modifier.padding(start = 12.dp)) },
                            onClick = tickAnd {
                                clearDuesInput = (duesAmount ?: 0.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                                clearDuesOpen = true
                                closeMenu()
                            }
                        )
                        if (canClearFees) DropdownMenuItem(
                            text = { Text("Clear Pending Fees", color = Emerald, modifier = Modifier.padding(start = 12.dp)) },
                            onClick = tickAnd {
                                clearFeesInput = (pendingAmount ?: 0.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                                clearFeesOpen = true
                                closeMenu()
                            }
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text(if ("account" in expandedGroups) "Account ▾" else "Account ▸") },
                onClick = { toggleGroup("account") }
            )
            AnimatedVisibility(visible = "account" in expandedGroups, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    if (canChangeStatus) DropdownMenuItem(
                        text = { Text("Change Status", color = YellowWarn, modifier = Modifier.padding(start = 12.dp)) },
                        onClick = tickAnd { changeStatusTarget = "PENDING"; pendingInput = ""; changeStatusOpen = true; closeMenu() }
                    )
                    DropdownMenuItem(
                        text = { Text("Message", color = Emerald, modifier = Modifier.padding(start = 12.dp)) },
                        onClick = tickAnd { msgOpen = true; closeMenu() }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Delete", color = RedAlert) },
                onClick = tickAnd { deleteOpen = true; closeMenu() }
            )
        }
    }

    // ── Change seat dialog ──────────────────────────────────────────────
    if (changeSeatOpen) {
        ConfirmDialog(
            title = "Change Seat – $name",
            onDismiss = { changeSeatOpen = false; newSeat = "" },
            onConfirm = {
                membershipId?.let { mid ->
                    vm.changeSeat(mid, newSeat) { changeSeatOpen = false; newSeat = ""; onMutated() }
                }
            },
            confirmLabel = "Apply",
            confirmEnabled = newSeat.isNotBlank(),
            body = {
                SeatGrid(seats = seats, selectedSeatNumber = newSeat.takeIf { it.isNotBlank() }, onSeatClick = { newSeat = it.seatNumber })
            }
        )
    }

    // ── Exchange seat dialog ─────────────────────────────────────────────
    // Swaps physical seats between $name and another currently-seated ACTIVE
    // student — each keeps their own plan/shift/dates, only seat_number trades.
    if (exchangeSeatOpen) {
        ConfirmDialog(
            title = "Exchange Seat – $name",
            subtitle = "Swap physical seats with another active student — plans and shifts stay unchanged.",
            onDismiss = { exchangeSeatOpen = false; exchangeSearch = ""; exchangeTarget = null },
            onConfirm = {
                val target = exchangeTarget ?: return@ConfirmDialog
                membershipId?.let { mid ->
                    vm.swapSeat(mid, target.id, target.seatNumber ?: "") {
                        exchangeSeatOpen = false; exchangeSearch = ""; exchangeTarget = null; onMutated()
                    }
                }
            },
            confirmLabel = "Exchange",
            confirmTone = ButtonTone.Amber,
            confirmEnabled = exchangeTarget != null,
            body = {
                if (exchangeTarget != null) {
                    val t = exchangeTarget!!
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AmberFaint)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(t.name, color = TextPrimary, fontSize = 14.sp)
                            Text("Seat ${t.seatNumber} · ${t.mobile}", color = TextSub, fontSize = 12.sp)
                        }
                        TextButton(onClick = { haptics.tick(); exchangeTarget = null }) {
                            Text("Change", color = Amber, fontSize = 12.sp)
                        }
                    }
                } else {
                    AppTextField(value = exchangeSearch, onValueChange = { exchangeSearch = it }, label = "Search name or mobile…")
                    Spacer(Modifier.height(8.dp))
                    if (swapCandidates.isEmpty()) {
                        Text("No active seated students found", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                            swapCandidates.forEach { st ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { haptics.tick(); exchangeTarget = st }
                                        .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(st.name, color = TextPrimary, fontSize = 13.sp)
                                        Text(st.mobile, color = TextSub, fontSize = 11.sp)
                                    }
                                    Text("Seat ${st.seatNumber}", color = Amber, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        )
    }

    // ── Delete confirmation ─────────────────────────────────────────────
    if (deleteOpen) {
        ConfirmDialog(
            title = "Delete Student",
            onDismiss = { deleteOpen = false },
            onConfirm = { vm.deleteStudent(studentId) { deleteOpen = false; onDeleted() } },
            confirmLabel = "Delete",
            confirmTone = ButtonTone.Danger,
            body = {
                Text(
                    "Permanently delete $name and all their memberships, payments, and seat bookings? This cannot be undone.",
                    color = TextSub, fontSize = 13.sp
                )
            }
        )
    }

    // ── Clear dues dialog ────────────────────────────────────────────────
    if (clearDuesOpen) {
        AmountClearDialog(
            title = "Clear Dues",
            outstanding = duesAmount ?: 0.0,
            amountInput = clearDuesInput,
            onAmountChange = { clearDuesInput = it },
            onDismiss = { clearDuesOpen = false; clearDuesInput = "" },
            onConfirm = {
                val amt = clearDuesInput.toDoubleOrNull()
                if (amt != null && amt > 0 && amt <= (duesAmount ?: 0.0)) {
                    membershipId?.let { vm.clearDues(it, amt) { clearDuesOpen = false; clearDuesInput = ""; onMutated() } }
                }
            }
        )
    }

    // ── Clear pending fees dialog ───────────────────────────────────────
    if (clearFeesOpen) {
        AmountClearDialog(
            title = "Clear Pending Fees",
            outstanding = pendingAmount ?: 0.0,
            amountInput = clearFeesInput,
            onAmountChange = { clearFeesInput = it },
            onDismiss = { clearFeesOpen = false; clearFeesInput = "" },
            onConfirm = {
                val amt = clearFeesInput.toDoubleOrNull()
                if (amt != null && amt > 0 && amt <= (pendingAmount ?: 0.0)) {
                    vm.clearPendingFees(studentId, amt) { clearFeesOpen = false; clearFeesInput = ""; onMutated() }
                }
            }
        )
    }

    // ── Change status dialog ────────────────────────────────────────────
    if (changeStatusOpen) {
        ConfirmDialog(
            title = "Change Status",
            subtitle = "Correct $name's wrongly-marked-Paid membership. This deletes their last payment record and cannot be undone.",
            onDismiss = { changeStatusOpen = false },
            onConfirm = {
                membershipId ?: return@ConfirmDialog
                if (changeStatusTarget == "PENDING") {
                    val amt = pendingInput.toDoubleOrNull()
                    if (amt != null && amt > 0) vm.markPending(membershipId, amt) { changeStatusOpen = false; onMutated() }
                } else {
                    vm.markGrace(membershipId) { changeStatusOpen = false; onMutated() }
                }
            },
            confirmLabel = "Save",
            confirmEnabled = changeStatusTarget != "PENDING" || pendingInput.toDoubleOrNull()?.let { it > 0 } == true,
            body = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = changeStatusTarget == "PENDING",
                        onClick = { haptics.tick(); changeStatusTarget = "PENDING" },
                        label = { Text("Pending") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = YellowFaint, selectedLabelColor = YellowWarn)
                    )
                    FilterChip(
                        selected = changeStatusTarget == "GRACE",
                        onClick = { haptics.tick(); changeStatusTarget = "GRACE" },
                        label = { Text("Grace") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrangeFaint, selectedLabelColor = Orange)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (changeStatusTarget == "PENDING") {
                    OutlinedTextField(
                        value = pendingInput, onValueChange = { pendingInput = it },
                        label = { Text("Pending Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber)
                    )
                } else {
                    Text(
                        "Sets dues to the full plan price and resets their expiry date to today, so days-overdue counts from now.",
                        color = TextSub, fontSize = 12.sp
                    )
                }
            }
        )
    }

    // ── Message dialog ──────────────────────────────────────────────────
    if (msgOpen) {
        ConfirmDialog(
            title = "Message $name",
            subtitle = "Sends via WhatsApp to $mobile",
            onDismiss = { msgOpen = false; msgText = "" },
            onConfirm = { vm.sendMessageToStudent(studentId, msgText.trim()) { msgOpen = false; msgText = "" } },
            confirmLabel = "Send",
            confirmTone = ButtonTone.Success,
            confirmEnabled = msgText.trim().length >= 5,
            body = {
                OutlinedTextField(
                    value = msgText, onValueChange = { msgText = it },
                    label = { Text("Message") }, modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Emerald, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Emerald)
                )
            }
        )
    }
}

@Composable
private fun AmountClearDialog(
    title: String,
    outstanding: Double,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val amt = amountInput.toDoubleOrNull()
    val valid = amt != null && amt > 0 && amt <= outstanding
    ConfirmDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = "Clear",
        confirmTone = ButtonTone.Success,
        confirmEnabled = valid,
        body = {
            Text("Outstanding: ₹${if (outstanding == outstanding.toLong().toDouble()) outstanding.toLong().toString() else outstanding.toString()}", color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountInput, onValueChange = onAmountChange,
                label = { Text("Amount to Clear (₹)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber)
            )
            if (amt != null && amt > 0 && amt < outstanding) {
                Spacer(Modifier.height(4.dp))
                Text("₹${(outstanding - amt).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }} will remain as a pending amount.", color = Amber, fontSize = 11.sp)
            }
        }
    )
}
