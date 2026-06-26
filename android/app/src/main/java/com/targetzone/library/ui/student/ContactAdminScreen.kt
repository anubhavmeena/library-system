package com.targetzone.library.ui.student

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.Membership
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.theme.*

@Composable
fun ContactAdminScreen(vm: StudentViewModel, membership: Membership?) {
    val adminContact  by vm.adminContact.collectAsState()
    val callAdminSent by vm.callAdminSent.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.loadAdminContact() }

    val hasActiveSeat = membership?.status == "ACTIVE" && membership.seatNumber.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Contact Admin", style = MaterialTheme.typography.headlineMedium, color = Amber)
        Text("Reach the library admin directly", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        // Admin contact info card
        AppCard(Modifier.fillMaxWidth()) {
            Text("👤 Admin Contact", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))

            if (adminContact == null) {
                repeat(3) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).padding(vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                adminContact?.name?.let { name ->
                    ContactRow(label = "Name", value = name)
                }

                adminContact?.mobile?.let { mobile ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mobile", color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                        Text(mobile, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.width(60.dp))
                        ContactChip("📞 Call") {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobile")))
                        }
                        ContactChip("💬 WhatsApp") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$mobile")))
                        }
                    }
                }

                adminContact?.email?.let { email ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Email", color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                        Text(email, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Spacer(Modifier.width(60.dp))
                        ContactChip("✉️ Email") {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                        }
                    }
                }

                if (adminContact?.mobile == null && adminContact?.email == null) {
                    Text("Contact details not available.", color = TextMuted, fontSize = 13.sp)
                }
            }
        }

        // Call Admin to Seat — only visible when student has an active seat
        if (hasActiveSeat) {
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("🙋", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Need help at your seat?",
                            color = BlueSoft,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Seat ${membership?.seatNumber} — tap to notify the admin via WhatsApp. They'll come to you.",
                            color = TextSub,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.callAdmin() },
                            enabled = !callAdminSent,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueSoft,
                                contentColor = NavyDeep,
                                disabledContainerColor = BlueSoft.copy(alpha = 0.5f),
                                disabledContentColor = NavyDeep
                            )
                        ) {
                            Text(
                                if (callAdminSent) "✓ Admin Notified" else "Call Admin to My Seat",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(60.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun ContactChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = BlueSoft.copy(alpha = 0.15f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = BlueSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
