package com.targetzone.library.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun AdminLoginScreen(vm: AuthViewModel, onSuccess: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var contact by remember { mutableStateOf("") }
    var otp     by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onSuccess() }

    Column(
        Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔐", fontSize = 48.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Admin Portal", style = MaterialTheme.typography.headlineMedium, color = Amber, textAlign = TextAlign.Center)
        Text(
            if (!state.otpSent) "Enter your email or mobile number" else "Enter the OTP sent to $contact",
            color = TextSub, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        state.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedFaint),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(it, color = RedAlert, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!state.otpSent) {
            AppTextField(
                value = contact,
                onValueChange = { contact = it.trim() },
                label = "Email or Mobile Number",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = if (state.isLoading) "Sending OTP…" else "Send OTP",
                enabled = contact.isNotBlank() && !state.isLoading,
                onClick = {
                    val contactType = if (contact.contains("@")) "EMAIL" else "MOBILE"
                    vm.sendOtp(contact, contactType)
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            AppTextField(
                value = otp,
                onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                label = "6-digit OTP",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = if (otp.isNotEmpty()) {
                    { TextButton(onClick = { otp = "" }) { Text("Clear", color = Amber, fontSize = 12.sp) } }
                } else null
            )
            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = if (state.isLoading) "Logging in…" else "Login",
                enabled = otp.length == 6 && !state.isLoading,
                onClick = { vm.adminLogin(contact, otp) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { vm.resetOtpState(); otp = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("← Change Contact", color = TextSub)
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Student Login", color = TextMuted)
        }
    }
}
