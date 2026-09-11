package com.n8n.mobile.studio.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class AgentRequest(val prompt: String, val tool: String? = null)

@Serializable
data class AgentResponse(val text: String? = null, val error: String? = null)

/**
 * Local bridge for developer tools such as Codex/OpenCode/OpenClaw/Hermes.
 * It talks only to a user-configured localhost endpoint; it does not bundle
 * or impersonate those tools and does not send requests to a cloud service.
 */
class LocalAgentBridge(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun run(baseUrl: String, request: AgentRequest): Result<AgentResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(AgentRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())
            val call = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/v1/execute")
                .post(body)
                .build()
            client.newCall(call).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Local agent returned HTTP ${response.code}")
                json.decodeFromString(AgentResponse.serializer(), text)
            }
        }
    }
}
