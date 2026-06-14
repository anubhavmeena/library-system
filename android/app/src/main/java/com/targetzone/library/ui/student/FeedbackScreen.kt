package com.targetzone.library.ui.student

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
import com.targetzone.library.data.model.FeedbackItem
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun FeedbackScreen(vm: StudentViewModel) {
    val feedback  by vm.myFeedback.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var type      by remember { mutableStateOf("FEEDBACK") }
    var subject   by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadFeedback() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Feedback", style = MaterialTheme.typography.headlineMedium)
            Text("Share your experience with us", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        if (submitted) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("✅", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Thank you for your feedback!", style = MaterialTheme.typography.titleMedium, color = Emerald)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { submitted = false; subject = ""; description = ""; type = "FEEDBACK" }) {
                            Text("Submit Another", color = Amber)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    // Type toggle
                    Text("Type", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("FEEDBACK" to "Feedback", "COMPLAINT" to "Complaint").forEach { (v, l) ->
                            FilterChip(
                                selected = type == v,
                                onClick = { type = v },
                                label = { Text(l, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberFaint,
                                    selectedLabelColor = Amber
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    AppTextField(value = subject, onValueChange = { subject = it }, label = "Subject *")
                    Spacer(Modifier.height(12.dp))
                    AppTextField(value = description, onValueChange = { description = it }, label = "Description *")
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = if (isLoading) "Submitting…" else "Submit",
                        enabled = subject.isNotBlank() && description.isNotBlank() && !isLoading,
                        onClick = {
                            vm.submitFeedback(type, subject.trim(), description.trim()) { submitted = true }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (feedback.isNotEmpty()) {
            item {
                Text("PREVIOUS FEEDBACK", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
            }
            items(feedback) { item ->
                FeedbackHistoryCard(item)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeedbackHistoryCard(item: FeedbackItem) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    color = if (item.type == "COMPLAINT") RedAlert.copy(alpha = 0.15f) else BlueSoft.copy(alpha = 0.15f)
                ) {
                    Text(
                        item.type.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 10.sp,
                        color = if (item.type == "COMPLAINT") RedAlert else BlueSoft,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                StatusBadge(item.status)
            }
            Text(item.createdAt.take(10), color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.subject, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(item.description, color = TextSub, fontSize = 12.sp)
        }
        if (!item.adminNotes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = Emerald.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("Admin note", fontSize = 10.sp, color = Emerald, fontWeight = FontWeight.SemiBold)
                    Text(item.adminNotes, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
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
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(label, fontSize = 10.sp, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
