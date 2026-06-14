package com.targetzone.library.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    sessionToken: String,
    onSuccess: () -> Unit
) {
    val state by vm.state.collectAsState()
    var name    by remember { mutableStateOf("") }
    var email   by remember { mutableStateOf("") }
    var dob     by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    // Gender dropdown
    var gender         by remember { mutableStateOf("") }
    var genderExpanded by remember { mutableStateOf(false) }
    val genderOptions = listOf("Male", "Female", "Other")

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
        Text("Welcome!", style = MaterialTheme.typography.headlineLarge, color = Amber, textAlign = TextAlign.Center)
        Text("Complete your registration", color = TextSub, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(it, color = RedAlert, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        AppTextField(value = name, onValueChange = { name = it }, label = "Full Name *")
        Spacer(Modifier.height(12.dp))
        AppTextField(value = email, onValueChange = { email = it }, label = "Email (optional)")
        Spacer(Modifier.height(12.dp))
        AppTextField(value = dob, onValueChange = { dob = it }, label = "Date of Birth (YYYY-MM-DD, optional)")
        Spacer(Modifier.height(12.dp))

        // Gender dropdown
        ExposedDropdownMenuBox(
            expanded = genderExpanded,
            onExpandedChange = { genderExpanded = !genderExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = gender,
                onValueChange = {},
                readOnly = true,
                label = { Text("Gender (optional)", color = TextMuted, fontSize = 13.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Amber
                ),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = genderExpanded,
                onDismissRequest = { genderExpanded = false },
                containerColor = NavyMid
            ) {
                genderOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = TextPrimary) },
                        onClick = { gender = option; genderExpanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Prefer not to say", color = TextMuted) },
                    onClick = { gender = ""; genderExpanded = false }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        AppTextField(value = address, onValueChange = { address = it }, label = "Address (optional)")
        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            text = if (state.isLoading) "Registering…" else "Create Account",
            enabled = name.isNotBlank() && !state.isLoading,
            onClick = {
                vm.register(
                    name.trim(),
                    email.takeIf { it.isNotBlank() },
                    sessionToken,
                    dateOfBirth = dob.takeIf { it.isNotBlank() },
                    gender = gender.takeIf { it.isNotBlank() },
                    address = address.takeIf { it.isNotBlank() }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
