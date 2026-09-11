package com.n8n.mobile.studio.ui.screens

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.n8n.mobile.studio.data.preferences.PreferencesStore
import com.n8n.mobile.studio.n8n.N8nApi
import com.n8n.mobile.studio.domain.models.N8nInstance
import com.n8n.mobile.studio.security.SecureStorage
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen() {
    val context = LocalContext.current
    val store = remember { PreferencesStore(context) }
    val secure = remember { SecureStorage(context) }
    val api = remember { N8nApi() }
    val scope = rememberCoroutineScope()
    val instances by store.instances.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Instances") }) }, floatingActionButton = {
        androidx.compose.material3.FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add instance") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            status?.let { Text(it, modifier = Modifier.padding(bottom = 12.dp)) }
            if (instances.isEmpty()) Text("No n8n instances configured. Add Cloud, self-hosted, staging, or production endpoints.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(instances) { instance ->
                    androidx.compose.material3.Card {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Column(Modifier.weight(1f)) { Text(instance.name); Text(instance.baseUrl); Text(instance.environment) }
                            Button(onClick = {
                                scope.launch {
                                    status = "Testing ${instance.name}…"
                                    val key = secure.get("n8n_api_${instance.id}")
                                    status = if (key != null && runCatching { api.testConnection(instance.baseUrl, key) }.getOrDefault(false)) "${instance.name}: connected" else "${instance.name}: connection failed"
                                }
                            }) { Text("Test") }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddInstanceDialog(onDismiss = { showAdd = false }) { name, url, env, key ->
        val id = UUID.randomUUID().toString()
        scope.launch {
            secure.put("n8n_api_$id", key)
            store.save(instances + N8nInstance(id, name, url.trimEnd('/'), env))
            showAdd = false
            status = "Instance saved securely"
        }
    }
}

@Composable private fun AddInstanceDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var env by remember { mutableStateOf("development") }
    var key by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add n8n instance") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") })
            OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true)
            OutlinedTextField(env, { env = it }, label = { Text("Environment") }, singleLine = true)
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true)
        }
    }, confirmButton = { Button(enabled = name.isNotBlank() && url.startsWith("http") && key.isNotBlank(), onClick = { onSave(name, url, env, key) }) { Text("Save") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}
