package com.n8n.mobile.studio.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class N8nApi(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun request(baseUrl: String, apiKey: String, path: String, method: String = "GET", body: String? = null): String {
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val builder = Request.Builder().url(url)
            .header("X-N8N-API-KEY", apiKey)
            .header("Accept", "application/json")
        when (method.uppercase()) {
            "POST" -> builder.post((body ?: "{}").toRequestBody())
            "PUT" -> builder.put((body ?: "{}").toRequestBody())
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody())
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("n8n request failed: HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    suspend fun testConnection(baseUrl: String, apiKey: String): Boolean {
        request(baseUrl, apiKey, "/api/v1/workflows")
        return true
    }
    suspend fun listWorkflows(baseUrl: String, apiKey: String): String = request(baseUrl, apiKey, "/api/v1/workflows")
    suspend fun getExecutions(baseUrl: String, apiKey: String): String = request(baseUrl, apiKey, "/api/v1/executions")
    suspend fun createWorkflow(baseUrl: String, apiKey: String, workflowJson: String): String = request(baseUrl, apiKey, "/api/v1/workflows", "POST", workflowJson)
    suspend fun updateWorkflow(baseUrl: String, apiKey: String, id: String, workflowJson: String): String = request(baseUrl, apiKey, "/api/v1/workflows/$id", "PUT", workflowJson)
    suspend fun deleteWorkflow(baseUrl: String, apiKey: String, id: String): String = request(baseUrl, apiKey, "/api/v1/workflows/$id", "DELETE")
}
