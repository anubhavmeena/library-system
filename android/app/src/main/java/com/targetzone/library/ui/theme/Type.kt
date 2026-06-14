package com.targetzone.library.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LibraryTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = TextPrimary),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = TextPrimary),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TextSub),
    bodySmall = TextStyle(fontSize = 12.sp, color = TextMuted),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)
