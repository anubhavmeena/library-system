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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onNavigateToRegister: (sessionToken: String) -> Unit,
    onLoginSuccess: () -> Unit,
    onAdminLogin: () -> Unit
) {
    val state by vm.state.collectAsState()
    var mobile by remember { mutableStateOf("") }
    var otp    by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoginSuccess() }
    LaunchedEffect(state.otpVerified, state.isNewUser) {
        if (state.otpVerified && state.isNewUser) onNavigateToRegister(state.sessionToken ?: "")
        else if (state.otpVerified && !state.isNewUser) vm.login(state.sessionToken ?: "")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📚", fontSize = 56.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Target Zone Library", style = MaterialTheme.typography.headlineMedium, color = Amber, textAlign = TextAlign.Center)
        Text("Student Login", style = MaterialTheme.typography.bodyMedium, color = TextSub, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(it, color = RedAlert, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!state.otpSent) {
            AppTextField(
                value = mobile, onValueChange = { mobile = it.filter(Char::isDigit).take(10) },
                label = "Mobile Number",
                leadingIcon = {
                    Text("+91", color = TextSub, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (state.isLoading) "Sending…" else "Send OTP",
                enabled = mobile.length == 10 && !state.isLoading,
                onClick = { vm.sendOtp(mobile) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text("OTP sent to +91$mobile", color = TextSub, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            AppTextField(
                value = otp, onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                label = "Enter 6-digit OTP",
                trailingIcon = if (otp.isNotEmpty()) {{ TextButton(onClick = { otp = "" }) { Text("Clear", color = Amber, fontSize = 12.sp) } }} else null
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (state.isLoading) "Verifying…" else "Verify OTP",
                enabled = otp.length == 6 && !state.isLoading,
                onClick = { vm.verifyOtp(mobile, otp) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.resetOtpState(); otp = "" }, modifier = Modifier.fillMaxWidth()) {
                Text("← Change Number", color = TextSub)
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdminLogin) {
            Text("Admin Login →", color = TextMuted, fontSize = 13.sp)
        }
    }
}
