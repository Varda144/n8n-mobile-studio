package com.n8n.mobile.studio.runtime

import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Aggregated reachability result for a runtime endpoint.
 */
data class RuntimeStatus(val reachable: Boolean, val detail: String)

/**
 * Probes whether a local runtime HTTP endpoint is serving.
 */
class RuntimeHealth(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, MILLISECONDS)
        .readTimeout(1500, MILLISECONDS)
        .build(),
) {

    /**
     * Probe [baseUrl]; any 2xx or 401..403 response counts as reachable.
     */
    suspend fun probe(baseUrl: String): RuntimeStatus = withContext(Dispatchers.IO) {
        probeSync(baseUrl)
    }

    private fun probeSync(baseUrl: String): RuntimeStatus {
        val endpoints = listOf(
            "$baseUrl/healthz",
            "$baseUrl/rest/workflows",
        )
        for (url in endpoints) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val code = response.code
                    if (code in 200..299 || code in 401..403) {
                        return RuntimeStatus(reachable = true, detail = "HTTP $code from $url")
                    }
                }
            } catch (_: Exception) {
                // try the next endpoint
            }
        }
        return RuntimeStatus(reachable = false, detail = "No response from $baseUrl")
    }
}