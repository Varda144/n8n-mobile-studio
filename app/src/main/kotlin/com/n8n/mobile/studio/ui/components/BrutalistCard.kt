package com.n8n.mobile.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BrutalistCard(modifier:Modifier=Modifier, content:@Composable ColumnScope.()->Unit){
    Card(
        modifier=modifier,
        shape=RoundedCornerShape(0.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF1B2336)),
        border=BorderStroke(2.dp, Color(0xFF334155))
    ){
        Column(modifier=Modifier.padding(16.dp), content=content)
    }
}
