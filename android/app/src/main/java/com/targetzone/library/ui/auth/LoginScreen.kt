package com.targetzone.library.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.R
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.BannerTone
import com.targetzone.library.ui.components.LibraryPhotoSlideshow
import com.targetzone.library.ui.components.MessageBanner
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*

// Drop library photos into res/drawable/ then list them here, e.g.:
//   R.drawable.library_photo_1, R.drawable.library_photo_2, …
private val LIBRARY_PHOTOS = listOf<Int>()

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
    val haptics = rememberLibraryHaptics()

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
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Target Zone Library", style = MaterialTheme.typography.headlineMedium, color = Amber, textAlign = TextAlign.Center)
        Text("Student Login", style = MaterialTheme.typography.bodyMedium, color = TextSub, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        LibraryPhotoSlideshow(
            photos = LIBRARY_PHOTOS,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        state.error?.let {
            MessageBanner(it, BannerTone.Error, modifier = Modifier.padding(bottom = 16.dp))
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
                trailingIcon = if (otp.isNotEmpty()) {{ TextButton(onClick = { haptics.tick(); otp = "" }) { Text("Clear", color = Amber, fontSize = 12.sp) } }} else null
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (state.isLoading) "Verifying…" else "Verify OTP",
                enabled = otp.length == 6 && !state.isLoading,
                onClick = { vm.verifyOtp(mobile, otp) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { haptics.tick(); vm.resetOtpState(); otp = "" }) {
                    Text("← Change Number", color = TextSub, fontSize = 13.sp)
                }
                val canResend = state.secondsSinceSend >= 10
                TextButton(onClick = { haptics.tick(); vm.resendOtp(mobile) }, enabled = canResend && !state.isLoading) {
                    Text(
                        if (canResend) "Resend OTP" else "Resend in ${10 - state.secondsSinceSend}s",
                        color = if (canResend) Amber else TextMuted, fontSize = 13.sp
                    )
                }
            }
            val showSmsOption = state.secondsSinceSend >= 10 && state.otpSendCount >= 2 && !state.smsOptionUsed
            if (showSmsOption) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { haptics.tick(); vm.sendOtpViaSms(mobile) }, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text("Still no OTP? Send via SMS instead", color = BlueSoft, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { haptics.tick(); onAdminLogin() }) {
            Text("Admin Login →", color = TextMuted, fontSize = 13.sp)
        }
    }
}
