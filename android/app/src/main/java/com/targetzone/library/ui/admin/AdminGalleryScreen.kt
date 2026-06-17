package com.targetzone.library.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.targetzone.library.data.model.GalleryPhoto
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@Composable
fun AdminGalleryScreen(vm: AdminViewModel) {
    val context     = LocalContext.current
    val photos      by vm.galleryPhotos.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val error       by vm.error.collectAsState()
    val successMsg  by vm.successMsg.collectAsState()

    var caption       by remember { mutableStateOf("") }
    var previewPhoto  by remember { mutableStateOf<GalleryPhoto?>(null) }
    var deleteTarget  by remember { mutableStateOf<GalleryPhoto?>(null) }
    var uploadDialog  by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        val bytes  = stream.readBytes(); stream.close()
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val part = MultipartBody.Part.createFormData(
            "file", "gallery.jpg",
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )
        val captionBody = if (caption.isNotBlank())
            caption.trim().toRequestBody("text/plain".toMediaTypeOrNull()) else null
        vm.uploadGalleryPhoto(part, captionBody) { caption = ""; uploadDialog = false }
    }

    LaunchedEffect(Unit) { vm.loadGallery() }
    LaunchedEffect(successMsg) {
        if (successMsg != null) { kotlinx.coroutines.delay(2500); vm.clearMessages() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Photo Gallery", style = MaterialTheme.typography.headlineMedium)
            Text("Manage library photos", color = TextSub, fontSize = 13.sp)
        }

        successMsg?.let {
            Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("✅  $it", color = Emerald, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No photos yet", color = TextMuted)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Upload First Photo", onClick = { uploadDialog = true })
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(photos) { photo ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyMid)
                            .clickable { previewPhoto = photo }
                    ) {
                        val imgUrl = if (photo.url.startsWith("http")) photo.url
                                     else "https://targetzone.co.in${photo.url}"
                        AsyncImage(
                            model = imgUrl,
                            contentDescription = photo.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (!photo.caption.isNullOrBlank()) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(photo.caption, color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        // Delete button
                        IconButton(
                            onClick = { deleteTarget = photo },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(0.55f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, null, tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Upload FAB
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            FloatingActionButton(
                onClick = { uploadDialog = true },
                containerColor = Amber,
                contentColor = NavyDark
            ) { Icon(Icons.Default.Add, "Upload Photo") }
        }
    }

    // Upload dialog
    if (uploadDialog) {
        Dialog(onDismissRequest = { if (!isLoading) uploadDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
                Column(Modifier.padding(20.dp)) {
                    Text("Upload Photo", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        placeholder = { Text("Caption (optional)", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber, unfocusedBorderColor = DividerColor,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { uploadDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSub),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                        ) { Text("Cancel") }
                        Button(
                            onClick = { pickImage.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDark)
                        ) { Text("Pick Image") }
                    }
                }
            }
        }
    }

    // Delete confirm dialog
    deleteTarget?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Photo?", color = TextPrimary) },
            text = { Text(if (!photo.caption.isNullOrBlank()) "\"${photo.caption}\"" else "This photo will be permanently deleted.", color = TextSub) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGalleryPhoto(photo.id) {}
                    deleteTarget = null
                    if (previewPhoto?.id == photo.id) previewPhoto = null
                }) { Text("Delete", color = RedAlert) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = Amber) }
            },
            containerColor = NavyMid
        )
    }

    // Lightbox
    previewPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { previewPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(0.92f))
                .clickable { previewPhoto = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val imgUrl = if (photo.url.startsWith("http")) photo.url
                                 else "https://targetzone.co.in${photo.url}"
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = photo.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).clip(RoundedCornerShape(12.dp))
                    )
                    if (!photo.caption.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(photo.caption, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { deleteTarget = photo },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedAlert.copy(0.5f))
                        ) { Text("Delete") }
                        Button(
                            onClick = { previewPhoto = null },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyMid, contentColor = TextPrimary)
                        ) { Text("Close") }
                    }
                }
            }
        }
    }
}
