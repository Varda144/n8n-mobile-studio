package com.n8n.mobile.studio.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.n8n.mobile.studio.domain.N8nInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.instanceDataStore by preferencesDataStore("instances")

class InstanceStore(private val context: Context) {
    private val key = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true }
    val instances: Flow<List<N8nInstance>> = context.instanceDataStore.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<List<N8nInstance>>(it) }.getOrNull() } ?: emptyList()
    }
    suspend fun save(items: List<N8nInstance>) {
        context.instanceDataStore.edit { it[key] = json.encodeToString(items) }
    }
}
