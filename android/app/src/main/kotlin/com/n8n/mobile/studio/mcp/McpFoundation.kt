package com.n8n.mobile.studio.mcp

import com.n8n.mobile.studio.security.RiskLevel

interface McpClient {
    suspend fun listTools(): List<McpTool>
    suspend fun callTool(name: String, argumentsJson: String, risk: RiskLevel): McpResult
}

data class McpServerConfig(val id: String, val name: String, val endpoint: String, val enabled: Boolean)
data class McpTool(val name: String, val description: String?)
sealed interface McpResult { data class Success(val content: String): McpResult; data class Failure(val message: String): McpResult }
