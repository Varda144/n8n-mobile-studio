package com.n8n.mobile.studio.ai

interface AiProvider {
    val id: String
    suspend fun generateWorkflow(instruction: String): String
    suspend fun explainWorkflow(workflowJson: String): String
    suspend fun validateWorkflow(workflowJson: String): List<String>
}

data class AiProviderConfig(val id: String, val baseUrl: String, val model: String)
