package com.n8n.mobile.studio.domain.repositories

import com.n8n.mobile.studio.data.preferences.PreferencesStore
import com.n8n.mobile.studio.domain.models.N8nInstance
import com.n8n.mobile.studio.n8n.N8nApi
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkflowRepository(
    private val api: N8nApi = N8nApi(),
    private val store: PreferencesStore,
) {
    suspend fun listWorkflowNames(instance: N8nInstance, apiKey: String): Result<List<String>> = runCatching {
        val raw = api.listWorkflows(instance.baseUrl, apiKey)
        val data = Json.parseToJsonElement(raw).jsonObject["data"] as? JsonArray ?: return@runCatching emptyList()
        data.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    suspend fun saveInstances(items: List<N8nInstance>) = store.save(items)

    fun instancesFlow(): Flow<List<N8nInstance>> = store.instances
}