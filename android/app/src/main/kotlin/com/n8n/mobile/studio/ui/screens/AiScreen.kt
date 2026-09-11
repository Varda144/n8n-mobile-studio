package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen() {
    var instruction by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("AI workflow generation is provider-agnostic. Configure an AI provider before generation.") }
    Scaffold(topBar = { TopAppBar(title = { Text("AI Workflow Builder") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(instruction, { instruction = it }, Modifier.fillMaxWidth(), label = { Text("Describe the workflow") })
            Button(onClick = { plan = if (instruction.isBlank()) "Enter a workflow description." else "Plan created. Review nodes and parameters before creating anything in n8n." }) { Text("Generate plan") }
            Text(plan)
        }
    }
}
