package com.n8n.mobile.studio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioBlue = Color(0xFF4F7CFF)
private val StudioBlueDark = Color(0xFFB9C7FF)
private val StudioBackground = Color(0xFFF7F8FC)
private val StudioSurface = Color(0xFFFFFFFF)
private val StudioDarkBackground = Color(0xFF101217)
private val StudioDarkSurface = Color(0xFF181A20)

private val LightColors = lightColorScheme(
    primary = StudioBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF5F6575),
    background = StudioBackground,
    surface = StudioSurface,
    surfaceVariant = Color(0xFFE9ECF4),
)

private val DarkColors = darkColorScheme(
    primary = StudioBlueDark,
    onPrimary = Color(0xFF152044),
    secondary = Color(0xFFC4C7D2),
    background = StudioDarkBackground,
    surface = StudioDarkSurface,
    surfaceVariant = Color(0xFF292C35),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
