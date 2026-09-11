package com.n8n.mobile.studio.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Local-only runtime/bridge configuration. The app never assumes a remote service is available. */
data class LocalRuntimeConfig(
    val n8nUrl: String = "http://127.0.0.1:5678",
    val agentUrl: String = "http://127.0.0.1:8765",
)

data class RuntimeStatus(
    val reachable: Boolean,
    val detail: String,
)

class LocalRuntimeProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build(),
) {
    suspend fun probe(baseUrl: String): RuntimeStatus = withContext(Dispatchers.IO) {
        val normalized = baseUrl.trimEnd('/')
        val candidates = listOf("$normalized/healthz", "$normalized/rest/workflows")
        for (url in candidates) {
            runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (response.isSuccessful || response.code in 401..403) {
                        return@withContext RuntimeStatus(true, "Local service reachable (${response.code})")
                    }
                }
            }
        }
        RuntimeStatus(false, "No local service detected at $normalized")
    }
}
