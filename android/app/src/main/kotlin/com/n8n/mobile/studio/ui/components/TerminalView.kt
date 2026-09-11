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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n8n.mobile.studio.terminal.TerminalOutput

@Composable
fun TerminalView(lines: List<TerminalOutput.Line>, modifier: Modifier = Modifier) {
    BrutalistSurface(modifier = modifier.fillMaxWidth(), shadow = false) {
        Box(Modifier.background(Color.White).fillMaxWidth().padding(12.dp)) {
            Column {
                if (lines.isEmpty()) {
                    Text("--- no output ---", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF737373))
                }
                lines.forEach { line ->
                    Text(
                        line.text,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (line.channel) {
                            TerminalOutput.Channel.STDERR -> Color(0xFFC62828)
                            TerminalOutput.Channel.STDOUT -> Color(0xFF000000)
                            TerminalOutput.Channel.SYSTEM -> Color(0xFF737373)
                        },
                    )
                }
            }
        }
    }
}