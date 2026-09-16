package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalistButton(text:String, onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true, primary:Boolean=true, destructive:Boolean=false){
    val bg = when{ destructive -> Color(0xFFEF4444); primary -> Color(0xFFF8FAFC); else -> Color(0xFF1B2336) }
    val fg = when{ destructive -> Color.White; primary -> Color(0xFF0F172A); else -> Color.White }
    val border = if(primary && !destructive) BorderStroke(2.dp, Color(0xFFF8FAFC)) else if(destructive) null else BorderStroke(2.dp, Color(0xFF475569))
    Button(
        onClick=onClick, enabled=enabled, modifier=modifier.height(48.dp),
        shape=RoundedCornerShape(0.dp),
        colors=ButtonDefaults.buttonColors(containerColor=bg, contentColor=fg, disabledContainerColor=Color(0xFF272F42), disabledContentColor=Color(0xFF64748B)),
        border=border,
        contentPadding=PaddingValues(horizontal=16.dp)
    ){
        Text(text, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=13.sp, letterSpacing=0.5.sp)
    }
}

@Composable
fun BrutalistOutlineButton(text:String, onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true){
    OutlinedButton(
        onClick=onClick, enabled=enabled, modifier=modifier.height(48.dp),
        shape=RoundedCornerShape(0.dp),
        border=BorderStroke(2.dp, Color(0xFF475569)),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)
    ){
        Text(text, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold, fontSize=13.sp)
    }
}
