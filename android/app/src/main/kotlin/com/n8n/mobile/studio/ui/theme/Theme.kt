package com.n8n.mobile.studio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

private val StudioTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-0.9).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
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

fun Modifier.spatialGridBackground(
    spacing: Dp = 18.dp,
    dotRadius: Dp = 0.8.dp,
    alpha: Float = 0.62f,
): Modifier = drawBehind {
    val step = spacing.toPx()
    val radius = dotRadius.toPx()
    val dotColor = StudioPalette.Grid.copy(alpha = alpha)
    var x = 0f
    while (x <= size.width) {
        var y = 0f
        while (y <= size.height) {
            drawCircle(dotColor, radius, androidx.compose.ui.geometry.Offset(x, y))
            y += step
        }
        x += step
    }
}

fun Modifier.brutalistShadow(offset: Dp = 6.dp): Modifier = drawBehind {
    translate(left = offset.toPx(), top = offset.toPx()) {
        drawRect(StudioPalette.Primary, size = size)
    }
}

fun Modifier.brutalistBorder(width: Dp = 2.dp): Modifier = border(width, StudioPalette.Primary)

@Composable
fun SpatialSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.background(StudioPalette.Background).spatialGridBackground(),
    ) { content() }
}