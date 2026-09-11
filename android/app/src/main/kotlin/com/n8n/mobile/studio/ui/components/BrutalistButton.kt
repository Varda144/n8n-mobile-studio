package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.ui.theme.StudioPalette
import com.n8n.mobile.studio.ui.theme.brutalistBorder
import com.n8n.mobile.studio.ui.theme.brutalistShadow

@Composable
fun BrutalistAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = StudioPalette.White,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .then(if (pressed) Modifier.brutalistShadow(3.dp) else Modifier.brutalistShadow())
            .background(background)
            .brutalistBorder(2.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun BrutalistButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = StudioPalette.Accent,
) {
    BrutalistAction(onClick = onClick, modifier = modifier, background = background) {
        Text(label, color = StudioPalette.Primary, fontWeight = FontWeight.Bold)
    }
}