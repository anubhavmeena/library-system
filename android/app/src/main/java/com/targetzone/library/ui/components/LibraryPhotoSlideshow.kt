package com.targetzone.library.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.targetzone.library.ui.theme.Amber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryPhotoSlideshow(photos: List<Int>, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { photos.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        while (true) {
            delay(4000L)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % photos.size)
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(state = pagerState) { page ->
            Image(
                painter = painterResource(photos[page]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(photos.size) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(
                            width = if (i == pagerState.currentPage) 20.dp else 8.dp,
                            height = 6.dp
                        )
                        .background(
                            color = if (i == pagerState.currentPage) Amber else Color.White.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(3.dp)
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                )
            }
        }
    }
}
