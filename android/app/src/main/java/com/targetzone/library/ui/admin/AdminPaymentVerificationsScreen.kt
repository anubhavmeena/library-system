package com.targetzone.library.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.targetzone.library.data.model.AdminPaymentClaimItem
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.ButtonTone
import com.targetzone.library.ui.components.ConfirmDialog
import com.targetzone.library.ui.theme.*

private val CLAIM_TYPE_LABELS = mapOf("DUES" to "Grace Dues", "PENDING_FEE" to "Pending Fee")

private fun claimImageUrl(url: String) = if (url.startsWith("http")) url else "https://targetzone.co.in$url"

@Composable
fun AdminPaymentVerificationsScreen(vm: AdminViewModel) {
    val claims by vm.paymentClaims.collectAsState()
    var reviewing by remember { mutableStateOf<Pair<AdminPaymentClaimItem, String>?>(null) } // claim to (VERIFIED|REJECTED)
    var previewUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadPaymentClaims() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Verify Payments", style = MaterialTheme.typography.headlineMedium)
            Text("${claims.size} pending verification(s)", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        if (claims.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing to review", color = TextMuted)
                }
            }
        } else {
            items(claims) { claim ->
                PaymentClaimCard(
                    claim = claim,
                    onPreview = { previewUrl = claimImageUrl(claim.screenshotUrl) },
                    onVerify = { reviewing = claim to "VERIFIED" },
                    onReject = { reviewing = claim to "REJECTED" },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    reviewing?.let { (claim, status) ->
        ConfirmDialog(
            title = if (status == "VERIFIED") "Verify Payment" else "Reject Claim",
            subtitle = "${claim.studentName} — ₹${"%.2f".format(claim.amountClaimed)} (${CLAIM_TYPE_LABELS[claim.claimType] ?: claim.claimType})",
            onDismiss = { reviewing = null },
            onConfirm = { vm.reviewPaymentClaim(claim.id, status) { reviewing = null } },
            confirmLabel = if (status == "VERIFIED") "Verify" else "Reject",
            confirmTone = if (status == "VERIFIED") ButtonTone.Success else ButtonTone.Danger,
        ) {
            Text(
                if (status == "VERIFIED")
                    "This will mark the claim verified and automatically clear the student's ${if (claim.claimType == "DUES") "grace dues" else "pending fee"}."
                else
                    "This will mark the claim rejected. No amount will be cleared.",
                color = TextSub, fontSize = 12.sp
            )
        }
    }

    previewUrl?.let { url ->
        Dialog(onDismissRequest = { previewUrl = null }) {
            AsyncImage(
                model = url,
                contentDescription = "Payment screenshot",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { previewUrl = null }
            )
        }
    }
}

@Composable
private fun PaymentClaimCard(
    claim: AdminPaymentClaimItem,
    onPreview: () -> Unit,
    onVerify: () -> Unit,
    onReject: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = claimImageUrl(claim.screenshotUrl),
                contentDescription = "Payment screenshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavyMid)
                    .clickable(onClick = onPreview)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(claim.studentName, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                if (!claim.studentMobile.isNullOrBlank()) {
                    Text(claim.studentMobile, color = TextSub, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = BlueSoft.copy(alpha = 0.15f)) {
                        Text(CLAIM_TYPE_LABELS[claim.claimType] ?: claim.claimType, fontSize = 10.sp, color = BlueSoft, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("₹${"%.2f".format(claim.amountClaimed)}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(claim.createdAt.replace("T", " ").take(16), color = TextMuted, fontSize = 10.sp)
            }
        }
        if (claim.status == "PENDING") {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReject) { Text("Reject", color = RedAlert, fontSize = 12.sp) }
                TextButton(onClick = onVerify) { Text("Verify", color = Emerald, fontSize = 12.sp) }
            }
        }
    }
}
