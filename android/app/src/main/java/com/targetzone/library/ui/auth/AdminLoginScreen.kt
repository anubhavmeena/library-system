package com.targetzone.library.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@Composable
fun AdminLoginScreen(vm: AuthViewModel, onSuccess: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPwd  by remember { mutableStateOf(false) }

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
        Text("Sign in with your admin credentials", color = TextSub, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(it, color = RedAlert, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        AppTextField(value = email, onValueChange = { email = it }, label = "Email Address")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password", color = TextSub) },
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(if (showPwd) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = TextSub)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            text = if (state.isLoading) "Signing in…" else "Sign In",
            enabled = email.isNotBlank() && password.isNotBlank() && !state.isLoading,
            onClick = { vm.adminLogin(email.trim(), password) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Student Login", color = TextMuted)
        }
    }
}
