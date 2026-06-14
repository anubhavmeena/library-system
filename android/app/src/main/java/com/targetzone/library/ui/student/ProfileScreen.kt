package com.targetzone.library.ui.student

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.targetzone.library.data.model.UpdateProfileRequest
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.theme.*
import java.io.File

@Composable
fun ProfileScreen(vm: StudentViewModel, onLogout: () -> Unit) {
    val profile  by vm.profile.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val context  = LocalContext.current
    var editing  by remember { mutableStateOf(false) }

    var name        by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var fatherName  by remember(profile) { mutableStateOf(profile?.fatherName ?: "") }
    var email       by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var address     by remember(profile) { mutableStateOf(profile?.address ?: "") }
    var dob         by remember(profile) { mutableStateOf(profile?.dateOfBirth ?: "") }

    LaunchedEffect(Unit) { vm.loadProfile() }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val file = uriToFile(context, it) ?: return@let
            // Upload in a coroutine via a side-effect
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = {
                if (editing) {
                    vm.updateProfile(UpdateProfileRequest(name = name, fatherName = fatherName.takeIf { it.isNotBlank() }, address = address.takeIf { it.isNotBlank() }, gender = profile?.gender, dateOfBirth = dob.takeIf { it.isNotBlank() }, email = email.takeIf { it.isNotBlank() }))
                    editing = false
                } else editing = true
            }) {
                Icon(if (editing) Icons.Default.Save else Icons.Default.Edit, contentDescription = if (editing) "Save" else "Edit", tint = Amber)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Avatar
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            if (!profile?.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile!!.photoUrl,
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(90.dp).clip(CircleShape).border(2.dp, Amber, CircleShape)
                )
            } else {
                Box(Modifier.size(90.dp).clip(CircleShape).background(NavyMid).border(2.dp, DividerColor, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        profile?.name?.split(" ")?.mapNotNull { it.firstOrNull()?.uppercaseChar() }?.take(2)?.joinToString("") ?: "S",
                        fontSize = 28.sp, color = Amber, fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Amber).align(Alignment.BottomEnd).clickable { photoLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.CameraAlt, null, tint = NavyDeep, modifier = Modifier.size(14.dp)) }
        }

        Spacer(Modifier.height(8.dp))
        Text(profile?.name ?: "", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(profile?.mobile ?: "", color = TextSub, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Text("Personal Information", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(value = name, onValueChange = { name = it }, label = "Full Name")
                    AppTextField(value = fatherName, onValueChange = { fatherName = it }, label = "Father's Name")
                    AppTextField(value = email, onValueChange = { email = it }, label = "Email")
                    AppTextField(value = address, onValueChange = { address = it }, label = "Address")
                    AppTextField(value = dob, onValueChange = { dob = it }, label = "Date of Birth (YYYY-MM-DD)")
                }
            } else {
                listOf(
                    "Full Name" to profile?.name,
                    "Father's Name" to profile?.fatherName,
                    "Email" to profile?.email,
                    "Mobile" to profile?.mobile,
                    "Date of Birth" to profile?.dateOfBirth,
                    "Gender" to profile?.gender,
                    "Address" to profile?.address
                ).forEach { (label, value) ->
                    if (!value.isNullOrBlank()) {
                        InfoRowLocal(label, value)
                    }
                }
            }
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Amber, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
            border = BorderStroke(1.dp, RedAlert.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Logout", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoRowLocal(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSub, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
}

private fun uriToFile(context: android.content.Context, uri: Uri): File? = runCatching {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { inputStream.copyTo(it) }
    file
}.getOrNull()
