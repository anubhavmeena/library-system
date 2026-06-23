package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.components.SectionHeader
import com.targetzone.library.ui.theme.*

@Composable
fun AdminBroadcastScreen(vm: AdminViewModel) {
    val isLoading       by vm.isLoading.collectAsState()
    val success         by vm.successMsg.collectAsState()
    val error           by vm.error.collectAsState()
    val broadcastHistory by vm.broadcastHistory.collectAsState()
    var message         by remember { mutableStateOf("") }
    var target          by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) { vm.loadBroadcastHistory() }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(3000); vm.clearMessages(); vm.loadBroadcastHistory() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Broadcast Message", style = MaterialTheme.typography.headlineMedium)
        Text("Send WhatsApp/SMS to students", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        success?.let {
            Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint), modifier = Modifier.fillMaxWidth()) {
                Text("✅  $it", color = Emerald, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Target Group", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All Students", "ACTIVE" to "Active Only", "EXPIRING" to "Expiring Soon").forEach { (v, l) ->
                    FilterChip(
                        selected = target == v, onClick = { target = v },
                        label = { Text(l, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        AppCard(Modifier.fillMaxWidth()) {
            Text("Message", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                placeholder = { Text("Type your broadcast message…", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(150.dp),
                maxLines = 8
            )
            Spacer(Modifier.height(4.dp))
            Text("${message.length} characters", color = TextMuted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = AmberFaint), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("This will send messages via WhatsApp & SMS to the selected group.", color = Amber, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (isLoading) "Sending…" else "Send Broadcast",
            enabled = message.isNotBlank() && !isLoading,
            onClick = { vm.sendBroadcast(message, target) { message = "" } },
            modifier = Modifier.fillMaxWidth()
        )

        if (broadcastHistory.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Broadcast History")
            Spacer(Modifier.height(8.dp))
            broadcastHistory.forEach { h ->
                AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(h.message, color = TextPrimary, fontSize = 13.sp, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text("${h.targetGroup} · ${h.recipientCount} recipients", color = TextMuted, fontSize = 11.sp)
                        }
                        if (!h.sentAt.isNullOrBlank()) {
                            Text(h.sentAt.take(10), color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
