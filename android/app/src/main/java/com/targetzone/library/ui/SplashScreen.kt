package com.targetzone.library.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    var iconVisible     by remember { mutableStateOf(false) }
    var titleVisible    by remember { mutableStateOf(false) }
    var subtitleVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        iconVisible  = true
        delay(300)
        titleVisible = true
        delay(300)
        subtitleVisible = true
        delay(1000)
        onComplete()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            AnimatedVisibility(
                visible = iconVisible,
                enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.7f)
            ) {
                Text("📚", fontSize = 72.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(
                visible = titleVisible,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }
            ) {
                Text(
                    "Target Zone Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Amber,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(
                visible = subtitleVisible,
                enter = fadeIn(tween(500))
            ) {
                Text(
                    "Your Smart Study Companion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSub,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
