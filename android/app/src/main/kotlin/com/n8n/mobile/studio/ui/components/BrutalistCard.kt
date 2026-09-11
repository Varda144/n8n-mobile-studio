package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.ui.theme.StudioPalette
import com.n8n.mobile.studio.ui.theme.brutalistBorder
import com.n8n.mobile.studio.ui.theme.brutalistShadow

@Composable
fun BrutalistSurface(
    modifier: Modifier = Modifier,
    background: Color = StudioPalette.White,
    shadow: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (shadow) Modifier.brutalistShadow() else Modifier)
            .background(background)
            .brutalistBorder(),
    ) { content() }
}

@Composable
fun BrutalistCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BrutalistSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                title,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioPalette.Primary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = StudioPalette.White,
                fontWeight = FontWeight.Black,
            )
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}