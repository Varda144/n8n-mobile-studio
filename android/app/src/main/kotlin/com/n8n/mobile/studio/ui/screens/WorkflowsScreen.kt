package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.n8n.mobile.studio.data.InstanceStore
import com.n8n.mobile.studio.data.api.N8nApi
import com.n8n.mobile.studio.security.SecureStorage
import com.n8n.mobile.studio.ui.BrutalistAction
import com.n8n.mobile.studio.ui.BrutalistSurface
import com.n8n.mobile.studio.ui.StudioPalette
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(onEditor: () -> Unit) {
    val context = LocalContext.current
    val store = remember { InstanceStore(context) }
    val secure = remember { SecureStorage(context) }
    val api = remember { N8nApi() }
    val scope = rememberCoroutineScope()
    val instances by store.instances.collectAsState(initial = emptyList())
    var names by remember { mutableStateOf(emptyList<String>()) }
    var message by remember { mutableStateOf("SELECT AN INSTANCE AND REFRESH WORKFLOWS") }

    Scaffold(
        containerColor = StudioPalette.Background,
        topBar = { TopAppBar(title = { Text("WORKFLOWS", fontWeight = FontWeight.Black) }) },
        floatingActionButton = {
            BrutalistAction(onClick = onEditor, background = StudioPalette.Accent) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Create workflow")
                    Text("NEW", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (instances.isEmpty()) {
                BrutalistSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "ADD AN N8N INSTANCE FIRST FROM DASHBOARD → INSTANCES.",
                        modifier = Modifier.padding(18.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                BrutalistAction(
                    onClick = {
                        val instance = instances.first()
                        scope.launch {
                            message = "LOADING…"
                            val key = secure.get("n8n_api_${instance.id}")
                            if (key == null) { message = "MISSING SECURE API KEY"; return@launch }
                            runCatching { api.listWorkflows(instance.baseUrl, key) }
                                .onSuccess { raw ->
                                    names = runCatching {
                                        (Json.parseToJsonElement(raw).jsonObject["data"] as? JsonArray)
                                            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                                            ?: emptyList()
                                    }.getOrDefault(emptyList())
                                    message = "LOADED ${names.size} WORKFLOWS"
                                }
                                .onFailure { message = (it.message ?: "REQUEST FAILED").uppercase() }
                        }
                    },
                    background = StudioPalette.Primary,
                ) { Text("REFRESH", color = StudioPalette.White, fontWeight = FontWeight.Black) }
            }

            Text(message, color = StudioPalette.Muted, fontWeight = FontWeight.Bold)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(names) { name ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioPalette.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, StudioPalette.Primary),
                    ) {
                        Text(name, Modifier.padding(18.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
