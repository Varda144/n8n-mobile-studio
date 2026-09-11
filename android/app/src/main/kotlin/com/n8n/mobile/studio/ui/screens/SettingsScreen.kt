package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card { Column(Modifier.padding(16.dp)) { Text("Security"); Text("API keys use Android Keystore-backed encryption.") } }
            Card { Column(Modifier.padding(16.dp)) { Text("Offline cache"); Text("Room + DataStore foundations are enabled.") } }
            Card { Column(Modifier.padding(16.dp)) { Text("Build"); Text("N8N Mobile Studio 0.1.0") } }
        }
    }
}
