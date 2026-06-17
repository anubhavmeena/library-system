package com.targetzone.library.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.targetzone.library.data.model.GalleryPhoto
import com.targetzone.library.ui.theme.*

@Composable
fun StudentGalleryScreen(vm: StudentViewModel) {
    val photos    by vm.galleryPhotos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.error.collectAsState()

    var preview by remember { mutableStateOf<GalleryPhoto?>(null) }

    LaunchedEffect(Unit) { vm.loadGallery() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Photo Gallery", style = MaterialTheme.typography.headlineMedium)
            Text("Library moments", color = TextSub, fontSize = 13.sp)
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
                    Text("No photos have been added yet", color = TextMuted)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos) { photo ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyMid)
                            .clickable { preview = photo }
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
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(photo.caption, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    // Lightbox
    preview?.let { photo ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.92f)).clickable { preview = null },
                contentAlignment = Alignment.Center
            ) {
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
                        Text(photo.caption, color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { preview = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyMid, contentColor = TextPrimary)
                    ) { Text("Close") }
                }
            }
        }
    }
}
