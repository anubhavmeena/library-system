package com.targetzone.library.ui.student

import androidx.compose.foundation.clickable
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
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun FeedbackScreen(vm: StudentViewModel) {
    val feedback  by vm.myFeedback.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var message   by remember { mutableStateOf("") }
    var rating    by remember { mutableIntStateOf(5) }
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
                        TextButton(onClick = { submitted = false; message = ""; rating = 5 }) { Text("Submit Another", color = Amber) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Your Rating", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { star ->
                            Text(
                                if (star <= rating) "★" else "☆",
                                fontSize = 32.sp,
                                color = if (star <= rating) Amber else TextMuted,
                                modifier = Modifier.clickable { rating = star }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    AppTextField(value = message, onValueChange = { message = it }, label = "Your feedback (min. 10 characters)")
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = if (isLoading) "Submitting…" else "Submit Feedback",
                        enabled = message.length >= 10 && !isLoading,
                        onClick = { vm.submitFeedback(message, rating) { submitted = true } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (feedback.isNotEmpty()) {
            item { Text("PREVIOUS FEEDBACK", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.5.sp) ; Spacer(Modifier.height(8.dp)) }
            items(feedback) { item ->
                AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row {
                            (1..5).forEach { star -> Text(if (star <= item.rating) "★" else "☆", fontSize = 14.sp, color = if (star <= item.rating) Amber else TextMuted) }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(item.createdAt.take(10), color = TextMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.message, color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}
