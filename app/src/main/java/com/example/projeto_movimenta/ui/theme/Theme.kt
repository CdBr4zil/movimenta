package com.example.projeto_movimenta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = DeepPetrol, onPrimary = Color.White,
    primaryContainer = Mint, onPrimaryContainer = Color(0xFF005B58),
    secondary = Petrol, onSecondary = Color.White,
    secondaryContainer = Mint, onSecondaryContainer = Color(0xFF005B58),
    tertiary = Color(0xFF805300), onTertiary = Color.White,
    background = Ice, onBackground = Ink,
    surface = Paper, onSurface = Ink, onSurfaceVariant = Slate,
    surfaceVariant = Color(0xFFE3EBEF), surfaceContainer = Color(0xFFF1F7F8),
    surfaceContainerHigh = Paper, surfaceContainerHighest = Color(0xFFE3EBEF),
    outline = Border, outlineVariant = Color(0xFFD1E0E3),
    error = Color(0xFFAA2738), onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = BrightMint, onPrimary = Color(0xFF043433),
    primaryContainer = DarkMint, onPrimaryContainer = DarkInk,
    secondary = BrightMint, onSecondary = Color(0xFF043433),
    secondaryContainer = DarkMint, onSecondaryContainer = BrightMint,
    tertiary = Color(0xFFFFCF80), onTertiary = Color(0xFF432A00),
    background = DarkIce, onBackground = DarkInk,
    surface = DarkPaper, onSurface = DarkInk, onSurfaceVariant = DarkSlate,
    surfaceVariant = Color(0xFF264146), surfaceContainer = Color(0xFF1D363A),
    surfaceContainerHigh = Color(0xFF233C40), surfaceContainerHighest = Color(0xFF294447),
    outline = DarkBorder, outlineVariant = Color(0xFF446164),
    error = Color(0xFFFFB3BE), onError = Color(0xFF5A1220),
)

private val MovimentaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal object Layout {
    val gutter = 16.dp
    val gap = 12.dp
    val compactGap = 8.dp
    val target = 48.dp
    val contentWidth = 640.dp
}

@Composable
fun Projeto_MovimentaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = Typography,
        shapes = MovimentaShapes,
        content = content,
    )
}
