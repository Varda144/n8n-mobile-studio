package com.n8n.mobile.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.data.InstanceStore
import com.n8n.mobile.studio.data.api.N8nApi
import com.n8n.mobile.studio.security.SecureStorage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(onEditor: () -> Unit) {
    val context = LocalContext.current
    val store = remember { InstanceStore(context) }
    val secure = remember { SecureStorage(context) }
    val api = remember { N8nApi() }
    val scope = rememberCoroutineScope()
    val instances by store.instances.collectAsState(initial = emptyList())
    var names by remember { mutableStateOf(emptyList<String>()) }
    var message by remember { mutableStateOf("Select an instance and refresh workflows") }
    Scaffold(topBar = { TopAppBar(title = { Text("Workflows") }) }, floatingActionButton = { FloatingActionButton(onClick = onEditor) { Icon(Icons.Default.Add, contentDescription = "Create workflow") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (instances.isEmpty()) Text("Add an n8n instance first from Dashboard → Instances.") else {
                Button(onClick = {
                    val instance = instances.first()
                    scope.launch {
                        message = "Loading…"
                        val key = secure.get("n8n_api_${instance.id}")
                        if (key == null) { message = "Missing secure API key"; return@launch }
                        runCatching { api.listWorkflows(instance.baseUrl, key) }
                            .onSuccess { raw ->
                                names = runCatching {
                                    (Json.parseToJsonElement(raw).jsonObject["data"] as? JsonArray)?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()
                                }.getOrDefault(emptyList())
                                message = "Loaded ${names.size} workflows"
                            }
                            .onFailure { message = it.message ?: "Request failed" }
                    }
                }) { Text("Refresh") }
            }
            Text(message)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(names) { name -> Card(Modifier.fillMaxWidth()) { Text(name, Modifier.padding(16.dp)) } } }
        }
    }
}
