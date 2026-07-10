package com.targetzone.library.ui.admin

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.AppTextField
import com.targetzone.library.ui.components.BannerTone
import com.targetzone.library.ui.components.MessageBanner
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*
import com.yalantis.ucrop.UCrop
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@Composable
fun AdminImportScreen(vm: AdminViewModel) {
    val context     = LocalContext.current
    val isLoading   by vm.isLoading.collectAsState()
    val result      by vm.importResult.collectAsState()
    val success     by vm.successMsg.collectAsState()
    val error       by vm.error.collectAsState()

    // Manual single import state
    var manualName    by remember { mutableStateOf("") }
    var manualPhone   by remember { mutableStateOf("") }
    var manualPhoto   by remember { mutableStateOf<File?>(null) }

    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    val haptics = rememberLibraryHaptics()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri  = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
            vm.importResult.value = null
        }
    }

    // Optional passport-style photo for the manual single-student form — live
    // camera capture, then crop, mirroring iOS's CameraCaptureView → PassportCropView.
    var cameraError      by remember { mutableStateOf<String?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCropFile   by remember { mutableStateOf<File?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                pendingCropFile?.takeIf { it.exists() && it.length() > 0 }?.let { manualPhoto = it }
                pendingCropFile = null
            }
            UCrop.RESULT_ERROR -> pendingCropFile = null
        }
    }

    fun launchCrop(sourceUri: Uri) {
        val destFile = File(context.cacheDir, "import_photo_${System.currentTimeMillis()}.jpg")
        pendingCropFile = destFile
        val uCropIntent = UCrop.of(sourceUri, Uri.fromFile(destFile))
            .withAspectRatio(3f, 4f)
            .withMaxResultSize(600, 800)
            .getIntent(context)
        cropLauncher.launch(uCropIntent)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (captured && uri != null) launchCrop(uri)
    }

    fun openCamera() {
        val captureFile = File(context.cacheDir, "import_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
        pendingCaptureUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() else cameraError = "Camera permission is required to take a photo"
    }

    fun requestCameraAndCapture() {
        cameraError = null
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) openCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(4000); vm.clearMessages() }
    }

    fun upload() {
        val uri = selectedUri ?: return
        val stream = context.contentResolver.openInputStream(uri) ?: return
        val bytes  = stream.readBytes()
        stream.close()

        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val reqBody  = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part     = MultipartBody.Part.createFormData("file", selectedName, reqBody)
        vm.importStudents(part)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Import Students", style = MaterialTheme.typography.headlineMedium)
        Text("Add a single student manually or import from file", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // ── Manual single import ──────────────────────────────────────────────
        AppCard(Modifier.fillMaxWidth()) {
            Text("Add Single Student", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Register a student directly by name and phone number, without going through the app. " +
                    "You can optionally add a passport-style photo.",
                color = TextMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(value = manualName, onValueChange = { manualName = it }, label = "Full Name *")
            Spacer(Modifier.height(6.dp))
            AppTextField(value = manualPhone, onValueChange = { manualPhone = it }, label = "Phone Number *")
            Spacer(Modifier.height(12.dp))

            if (manualPhoto != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = manualPhoto,
                        contentDescription = "Student photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 56.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { haptics.tick(); requestCameraAndCapture() }) {
                        Text("Retake Photo", color = Amber, fontSize = 13.sp)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { haptics.tick(); requestCameraAndCapture() }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Take Photo (optional)", color = Amber, fontSize = 14.sp)
                }
            }
            cameraError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = RedAlert, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (isLoading) "Registering…" else "Register Student",
                enabled = manualName.isNotBlank() && manualPhone.isNotBlank() && !isLoading,
                onClick = {
                    vm.importSingleStudent(manualName.trim(), manualPhone.trim(), manualPhoto) {
                        manualName = ""; manualPhone = ""; manualPhoto = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(16.dp))
        Text("Bulk Import from File", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(12.dp))

        // Format hint
        AppCard(Modifier.fillMaxWidth()) {
            Text("📋 Expected columns:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("S.No, Name, Phone, Fees Paid, Date (dd-MM-yyyy), Seat", color = TextSub, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Feedback banners
        success?.let {
            MessageBanner("✅  $it", BannerTone.Success)
            Spacer(Modifier.height(8.dp))
        }
        error?.let {
            MessageBanner("⚠️  $it", BannerTone.Error)
            Spacer(Modifier.height(8.dp))
        }

        // File picker area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, if (selectedUri != null) Amber.copy(alpha = 0.5f) else DividerColor, RoundedCornerShape(16.dp))
                .clickable { haptics.tick(); filePicker.launch("*/*") }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (selectedUri != null) "📄" else "⬆️", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                if (selectedUri != null) {
                    Text(selectedName, color = TextPrimary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to change file", color = TextMuted, fontSize = 12.sp)
                } else {
                    Text("Tap to select CSV or Excel file", color = TextSub, textAlign = TextAlign.Center, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Accepts .csv and .xlsx", color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        if (selectedUri != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { haptics.tick(); selectedUri = null; selectedName = ""; vm.importResult.value = null },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Remove", color = RedAlert, fontSize = 12.sp) }
        }

        Spacer(Modifier.height(12.dp))

        PrimaryButton(
            text = if (isLoading) "Importing…" else "Import File",
            enabled = selectedUri != null && !isLoading,
            onClick = { upload() },
            modifier = Modifier.fillMaxWidth()
        )

        // Results
        result?.let { r ->
            Spacer(Modifier.height(20.dp))
            Text("Import Results", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Total", r.totalRows.toString(), TextPrimary, Modifier.weight(1f))
                StatBox("Imported", r.imported.toString(), Emerald, Modifier.weight(1f))
                StatBox("Skipped", r.skipped.toString(), if (r.skipped > 0) RedAlert else TextMuted, Modifier.weight(1f))
            }

            if (r.errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Row Errors", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                r.errors.forEach { err ->
                    AppCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Row ${err.row}", color = RedAlert, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(err.phone, color = TextMuted, fontSize = 12.sp)
                        }
                        if (err.name.isNotBlank()) Text(err.name, color = TextPrimary, fontSize = 13.sp)
                        Text(err.reason, color = RedAlert, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// Previously its own ad-hoc 12dp-rounded Card — folded into the shared AppCard
// so this screen doesn't carry a 3rd, near-identical card abstraction.
@Composable
private fun StatBox(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(label, color = TextMuted, fontSize = 11.sp)
        }
    }
}
