package com.targetzone.library.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.R
import com.targetzone.library.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    var iconVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        iconVisible = true
        delay(350)
        textVisible = true
        delay(900)
        onComplete()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Book icon — scale + fade in
            AnimatedVisibility(
                visible = iconVisible,
                enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.7f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
            }

            // "TARGET ZONE" — horizontal clip-reveal (Slack style)
            AnimatedVisibility(
                visible = textVisible,
                enter = expandHorizontally(
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Start
                ) + fadeIn(tween(300))
            ) {
                Text(
                    "TARGET ZONE",
                    color = Amber,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}
