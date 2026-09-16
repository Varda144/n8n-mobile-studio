package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalView(output:String, modifier:Modifier=Modifier){
    val scroll = rememberScrollState()
    Box(modifier=modifier.background(Color.Black).border(2.dp, Color(0xFF334155)).padding(10.dp)){
        Text(output.ifEmpty{"\$ _"}, fontFamily=FontFamily.Monospace, fontSize=12.sp, color=Color(0xFF22C55E), modifier=Modifier.verticalScroll(scroll))
    }
}
