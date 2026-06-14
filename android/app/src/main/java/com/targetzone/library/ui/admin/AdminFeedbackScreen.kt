package com.targetzone.library.ui.admin

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
import com.targetzone.library.ui.theme.*

@Composable
fun AdminFeedbackScreen(vm: AdminViewModel) {
    val feedback  by vm.feedback.collectAsState()

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
                FeedbackCard(item)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeedbackCard(item: FeedbackItem) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(item.userName ?: "Student", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Row {
                    (1..5).forEach { star ->
                        Text(if (star <= item.rating) "★" else "☆", fontSize = 12.sp, color = if (star <= item.rating) Amber else TextMuted)
                    }
                }
            }
            Text(item.createdAt.take(10), color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.message, color = TextSub, fontSize = 13.sp)
    }
}
