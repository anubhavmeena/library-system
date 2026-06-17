package com.targetzone.library.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun AdminImportScreen(vm: AdminViewModel) {
    val context     = LocalContext.current
    val isLoading   by vm.isLoading.collectAsState()
    val result      by vm.importResult.collectAsState()
    val success     by vm.successMsg.collectAsState()
    val error       by vm.error.collectAsState()

    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri  = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
            vm.importResult.value = null
        }
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
        Text("Bulk-seed students from a CSV or Excel file", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // Format hint
        AppCard(Modifier.fillMaxWidth()) {
            Text("📋 Expected columns:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("S.No, Name, Phone, Fees Paid, Date (dd-MM-yyyy), Seat", color = TextSub, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Feedback banners
        success?.let {
            Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint), modifier = Modifier.fillMaxWidth()) {
                Text("✅  $it", color = Emerald, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        // File picker area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, if (selectedUri != null) Amber.copy(alpha = 0.5f) else DividerColor, RoundedCornerShape(16.dp))
                .clickable { filePicker.launch("*/*") }
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
                onClick = { selectedUri = null; selectedName = ""; vm.importResult.value = null },
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedFaint),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
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
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = NavyMid), modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(label, color = TextMuted, fontSize = 11.sp)
        }
    }
}
