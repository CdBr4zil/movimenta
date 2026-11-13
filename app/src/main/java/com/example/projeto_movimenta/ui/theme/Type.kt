package com.example.projeto_movimenta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Body = TextStyle(fontFamily = FontFamily.SansSerif)

internal val Typography = Typography(
    headlineSmall = Body.copy(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = Body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = Body.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Body.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Body.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Body.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = Body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = Body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
)
