package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntOffset
import kotlin.math.roundToInt

@Composable
fun EditorScreen(onBack: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var nodeOffset by remember { mutableStateOf(Offset(160f, 220f)) }
    Scaffold(topBar = { TopAppBar(title = { Text("Workflow Editor") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = {}) { Icon(Icons.Default.Save, null) } }) }, floatingActionButton = { FloatingActionButton(onClick = { zoom = (zoom + .1f).coerceAtMost(2.5f) }) { Text("+") } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF0B1020)).pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); nodeOffset += drag } }) {
            Canvas(Modifier.fillMaxSize()) {
                val p1 = nodeOffset
                val p2 = nodeOffset + Offset(260f * zoom, 0f)
                drawLine(Color(0xFF6EE7B7), p1, p2, 6f)
                drawCircle(Color(0xFF1F2937), 74f * zoom, p1)
                drawCircle(Color(0xFF1F2937), 74f * zoom, p2)
            }
            Text("Webhook", color = Color.White, modifier = Modifier.offset { IntOffset(nodeOffset.x.roundToInt() - 36, nodeOffset.y.roundToInt() - 10) })
            Text("HTTP Request", color = Color.White, modifier = Modifier.offset { IntOffset((nodeOffset.x + 220f * zoom).roundToInt(), (nodeOffset.y - 10).roundToInt()) })
        }
    }
}
