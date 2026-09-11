package com.n8n.mobile.studio.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.n8n.mobile.studio.domain.models.N8nInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.store by preferencesDataStore("instances")

class PreferencesStore(private val context: Context) {
    private val key = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true }

    val instances: Flow<List<N8nInstance>> = context.store.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<List<N8nInstance>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun save(items: List<N8nInstance>) {
        context.store.edit { it[key] = json.encodeToString(items) }
    }
}