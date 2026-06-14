package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.targetzone.library.data.model.FeedbackItem
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.theme.*

@Composable
fun AdminFeedbackScreen(vm: AdminViewModel) {
    val feedback  by vm.feedback.collectAsState()
    var respondTo by remember { mutableStateOf<FeedbackItem?>(null) }

    LaunchedEffect(Unit) { vm.loadFeedback() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Student Feedback", style = MaterialTheme.typography.headlineMedium)
            Text("${feedback.size} total responses", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        if (feedback.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No feedback yet", color = TextMuted)
                }
            }
        } else {
            items(feedback) { item ->
                FeedbackCard(item, onRespond = { respondTo = item })
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Respond dialog
    respondTo?.let { item ->
        RespondDialog(
            item = item,
            onDismiss = { respondTo = null },
            onSave = { status, notes ->
                vm.updateFeedback(item.id, status, notes) { respondTo = null }
            }
        )
    }
}

@Composable
private fun FeedbackCard(item: FeedbackItem, onRespond: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.studentName ?: "Student", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                if (!item.studentMobile.isNullOrBlank()) {
                    Text(item.studentMobile, color = TextSub, fontSize = 11.sp)
                }
            }
            Text(item.createdAt.take(10), color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeBadge(item.type)
            StatusBadge(item.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.subject, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(item.description, color = TextSub, fontSize = 12.sp)
        }
        if (!item.adminNotes.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = Emerald.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Text("Note: ${item.adminNotes}", fontSize = 12.sp, color = TextPrimary, modifier = Modifier.padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRespond,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
            modifier = Modifier.height(34.dp)
        ) { Text("Respond", fontSize = 12.sp) }
    }
}

@Composable
private fun RespondDialog(item: FeedbackItem, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var status     by remember { mutableStateOf(item.status.ifBlank { "OPEN" }) }
    var adminNotes by remember { mutableStateOf(item.adminNotes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
            Column(Modifier.padding(20.dp)) {
                Text("Respond to Feedback", style = MaterialTheme.typography.titleMedium)
                Text(item.subject, color = TextSub, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))

                Text("Status", color = TextSub, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("OPEN" to "Open", "UNDER_REVIEW" to "Under Review", "RESOLVED" to "Resolved").forEach { (v, l) ->
                        FilterChip(
                            selected = status == v,
                            onClick = { status = v },
                            label = { Text(l, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = adminNotes,
                    onValueChange = { adminNotes = it },
                    label = { Text("Admin notes (optional)", color = TextMuted, fontSize = 12.sp) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber, unfocusedBorderColor = DividerColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextSub) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(status, adminNotes.takeIf { it.isNotBlank() }) },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val (color, label) = if (type == "COMPLAINT") RedAlert to "Complaint" else BlueSoft to "Feedback"
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, fontSize = 10.sp, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (color, label) = when (status) {
        "OPEN"         -> Amber to "Open"
        "UNDER_REVIEW" -> BlueSoft to "Under Review"
        "RESOLVED"     -> Emerald to "Resolved"
        else           -> TextMuted to status
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, fontSize = 10.sp, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
