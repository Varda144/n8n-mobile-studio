package com.n8n.mobile.studio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StudioPalette {
    val Background = Color(0xFFF8F8F3)
    val Primary = Color(0xFF000000)
    val Muted = Color(0xFF737373)
    val Grid = Color(0xFFD7D7D2)
    val Accent = Color(0xFFFFF5A8)
    val White = Color(0xFFFFFFFF)
}

private val StudioTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

private val BrutalistColors = lightColorScheme(
    primary = StudioPalette.Primary,
    onPrimary = StudioPalette.White,
    secondary = StudioPalette.Muted,
    onSecondary = StudioPalette.White,
    tertiary = StudioPalette.Accent,
    onTertiary = StudioPalette.Primary,
    background = StudioPalette.Background,
    onBackground = StudioPalette.Primary,
    surface = StudioPalette.White,
    onSurface = StudioPalette.Primary,
    surfaceVariant = StudioPalette.Background,
    onSurfaceVariant = StudioPalette.Muted,
    outline = StudioPalette.Primary,
    error = StudioPalette.Primary,
    onError = StudioPalette.White,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrutalistColors,
        typography = StudioTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        ),
        content = content,
    )
}
