package com.n8n.mobile.studio.n8n

import com.n8n.mobile.studio.data.api.N8nApiClient

class N8nApi(private val client: N8nApiClient = N8nApiClient()) {
    suspend fun request(baseUrl: String, apiKey: String, path: String, method: String = "GET", body: String? = null): String =
        client.request(baseUrl, apiKey, path, method, body)

    suspend fun testConnection(baseUrl: String, apiKey: String): Boolean = client.testConnection(baseUrl, apiKey)

    suspend fun listWorkflows(baseUrl: String, apiKey: String): String = client.listWorkflows(baseUrl, apiKey)

    suspend fun getExecutions(baseUrl: String, apiKey: String): String = client.getExecutions(baseUrl, apiKey)

    suspend fun createWorkflow(baseUrl: String, apiKey: String, workflowJson: String): String =
        client.createWorkflow(baseUrl, apiKey, workflowJson)

    suspend fun updateWorkflow(baseUrl: String, apiKey: String, id: String, workflowJson: String): String =
        client.updateWorkflow(baseUrl, apiKey, id, workflowJson)

    suspend fun deleteWorkflow(baseUrl: String, apiKey: String, id: String): String =
        client.deleteWorkflow(baseUrl, apiKey, id)
}