package com.n8n.mobile.studio.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF8FAFC),
    onPrimary = Color(0xFF0F172A),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    tertiary = Color(0xFF22C55E),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1B2336),
    surfaceVariant = Color(0xFF272F42),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    error = Color(0xFFEF4444)
)

@Composable
fun N8nMobileStudioTheme(content:@Composable ()->Unit){
    MaterialTheme(colorScheme = DarkColors, typography = Typography(
        titleLarge = androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=22.sp, color=Color.White),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Monospace, fontSize=13.sp, color=Color(0xFFF8FAFC)),
        labelLarge = androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=14.sp)
    ), content=content)
}
