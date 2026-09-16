package com.n8n.mobile.studio.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
@Composable fun AiScreen(nav:NavController){ Box(Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(16.dp)){ Text("AiScreen — use Dashboard/n8n tabs", fontFamily=FontFamily.Monospace, fontSize=13.sp, color=Color(0xFF94A3B8)) } }
