package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.InboxSummary
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun AdminInboxScreen(vm: AdminViewModel) {
    val messages    by vm.inboxMessages.collectAsState()
    val selectedMsg by vm.selectedInboxMsg.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val success     by vm.successMsg.collectAsState()
    val error       by vm.error.collectAsState()

    LaunchedEffect(Unit) { vm.loadInbox() }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
    }

    if (selectedMsg != null) {
        MessageDetailView(vm = vm, onBack = { vm.selectedInboxMsg.value = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Inbox", style = MaterialTheme.typography.headlineMedium)
            Text("Messages from students", color = TextSub, fontSize = 13.sp)
        }

        error?.let {
            Text("⚠️ $it", color = RedAlert, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 13.sp)
        }

        if (isLoading && messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        } else if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📬", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Inbox is empty", color = TextSub)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(messages) { msg ->
                    InboxRow(msg) {
                        vm.loadInboxMessage(msg.messageNumber)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun InboxRow(msg: InboxSummary, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (!msg.isRead) Amber else NavyMid)
            )
            Column(Modifier.weight(1f)) {
                Text(msg.subject, fontWeight = if (!msg.isRead) FontWeight.SemiBold else FontWeight.Normal, color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                Text(msg.from, color = TextSub, fontSize = 12.sp)
            }
            Text(msg.date.take(10), color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MessageDetailView(vm: AdminViewModel, onBack: () -> Unit) {
    val msg      by vm.selectedInboxMsg.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val success  by vm.successMsg.collectAsState()
    val error    by vm.error.collectAsState()
    var replyText by remember { mutableStateOf("") }
    var showReply by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val m = msg ?: return

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = NavyMid,
            title = { Text("Delete Message?", color = TextPrimary) },
            text = { Text("This message will be permanently removed.", color = TextSub) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteInboxMessage(m.messageNumber) { onBack() } },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSub) } }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Amber)
            }
            Spacer(Modifier.width(4.dp))
            Text("Message", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAlert)
            }
        }
        Spacer(Modifier.height(8.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Text(m.subject, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("From: ${m.from}", color = TextSub, fontSize = 12.sp)
            Text("Date: ${m.date.take(10)}", color = TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Text(m.body, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        }

        Spacer(Modifier.height(16.dp))

        success?.let {
            Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint), modifier = Modifier.fillMaxWidth()) {
                Text("✅  $it", color = Emerald, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { showReply = !showReply },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
            border = ButtonDefaults.outlinedButtonBorder,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (showReply) "Cancel Reply" else "Reply") }

        if (showReply) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = replyText, onValueChange = { replyText = it },
                placeholder = { Text("Type your reply…", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(140.dp),
                maxLines = 8
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (isLoading) "Sending…" else "Send Reply",
                enabled = replyText.isNotBlank() && !isLoading,
                onClick = { vm.replyToMessage(m.messageNumber, replyText) { showReply = false; replyText = "" } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
