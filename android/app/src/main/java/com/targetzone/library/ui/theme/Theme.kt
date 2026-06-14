package com.targetzone.library.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Amber,
    onPrimary        = NavyDeep,
    primaryContainer = NavyMid,
    secondary        = Emerald,
    onSecondary      = NavyDeep,
    background       = NavyDeep,
    onBackground     = TextPrimary,
    surface          = NavyMid,
    onSurface        = TextPrimary,
    surfaceVariant   = CardBg,
    outline          = DividerColor,
    error            = RedAlert,
)

@Composable
fun LibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = LibraryTypography,
        content     = content
    )
}
