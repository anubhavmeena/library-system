package com.targetzone.library.ui.student

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun PaymentSuccessScreen(vm: StudentViewModel, onGoHome: () -> Unit) {
    val membership by vm.membership.collectAsState()

    LaunchedEffect(Unit) { vm.loadDashboard() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉", fontSize = 72.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Booking Confirmed!", style = MaterialTheme.typography.headlineMedium, color = Emerald, textAlign = TextAlign.Center)
        Text("Welcome to Target Zone Library", color = TextSub, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        if (membership != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your Seat", color = TextSub, fontSize = 13.sp)
                Text(membership!!.seatNumber, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Amber)
                Text(membership!!.shift, color = TextSub, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Valid until ${membership!!.endDate}", color = TextPrimary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
        PrimaryButton("Go to Dashboard", onClick = onGoHome, modifier = Modifier.fillMaxWidth())
    }
}
