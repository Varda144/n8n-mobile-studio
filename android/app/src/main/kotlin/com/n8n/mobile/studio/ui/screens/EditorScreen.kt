package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.n8n.mobile.studio.ui.components.BrutalistAction
import com.n8n.mobile.studio.ui.theme.StudioPalette
import com.n8n.mobile.studio.ui.theme.spatialGridBackground
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onBack: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var nodeOffset by remember { mutableStateOf(Offset(160f, 220f)) }

    Scaffold(
        containerColor = StudioPalette.Background,
        topBar = {
            TopAppBar(
                title = { Text("WORKFLOW EDITOR", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = {}) { Icon(Icons.Default.Save, "Save") } },
            )
        },
        floatingActionButton = {
            BrutalistAction(
                onClick = { zoom = (zoom + .1f).coerceAtMost(2.5f) },
                background = StudioPalette.Accent,
            ) { Text("ZOOM +", fontWeight = FontWeight.Black) }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                .padding(padding)
                .background(StudioPalette.Background)
                .spatialGridBackground()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        nodeOffset += drag
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val p1 = nodeOffset
                val p2 = nodeOffset + Offset(260f * zoom, 0f)
                drawLine(StudioPalette.Primary, p1, p2, 3f)
                drawCircle(StudioPalette.White, 74f * zoom, p1)
                drawCircle(StudioPalette.White, 74f * zoom, p2)
                drawCircle(StudioPalette.Primary, 74f * zoom, p1, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawCircle(StudioPalette.Primary, 74f * zoom, p2, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }

            Box(Modifier.offset { IntOffset(nodeOffset.x.roundToInt() - 70, nodeOffset.y.roundToInt() - 28) }) {
                BrutalistAction(onClick = {}, background = StudioPalette.White) {
                    Row {
                        Text("WEBHOOK", fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("01", color = StudioPalette.Muted, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(Modifier.offset { IntOffset((nodeOffset.x + 220f * zoom).roundToInt(), (nodeOffset.y - 28).roundToInt()) }) {
                BrutalistAction(onClick = {}, background = StudioPalette.White) {
                    Text("HTTP REQUEST", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
