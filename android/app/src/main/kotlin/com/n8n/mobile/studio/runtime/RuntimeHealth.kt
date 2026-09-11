package com.n8n.mobile.studio.runtime

import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Aggregated reachability result for a runtime endpoint.
 *
 * [reachable] means "the local process is serving HTTP", which is stronger than
 * "the process is alive": the supervisor only reports [EmbeddedProcessState.RUNNING]
 * after a successful probe.
 */
data class RuntimeStatus(
    val reachable: Boolean,
    val detail: String,
    val httpCode: Int? = null,
    val latencyMs: Long = 0,
    val checkedAt: Long = 0,
    val url: String = "",
)

/**
 * Probes the loopback HTTP endpoint of an embedded runtime.
 *
 * Only loopback URLs are ever probed — the app has no remote-server fallback, so
 * a reachable endpoint always means the app's own process is serving.
 */
class RuntimeHealth(
    private val client: OkHttpClient = defaultClient(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun probe(baseUrl: String, paths: List<String> = DEFAULT_PATHS): RuntimeStatus =
        withContext(Dispatchers.IO) {
            var lastDetail = "no response"
            paths.forEach { path ->
                val url = baseUrl.trimEnd('/') + if (path.startsWith("/")) path else "/$path"
                val started = now()
                try {
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                        val code = response.code
                        val elapsed = now() - started
                        // 401/403/404 still prove a live local server is answering.
                        if (code in 200..299 || code in 401..403) {
                            return@withContext RuntimeStatus(
                                reachable = true,
                                detail = "HTTP $code from $url",
                                httpCode = code,
                                latencyMs = elapsed,
                                checkedAt = now(),
                                url = url,
                            )
                        }
                        if (code in 404..499) {
                            lastDetail = "HTTP $code from $url"
                        }
                    }
                } catch (t: Throwable) {
                    lastDetail = "${t.javaClass.simpleName}: ${t.message ?: "no response"}"
                }
            }
            RuntimeStatus(reachable = false, detail = lastDetail, checkedAt = now(), url = baseUrl)
        }

    companion object {
        val DEFAULT_PATHS: List<String> = listOf("/")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(1_500, MILLISECONDS)
            .readTimeout(1_500, MILLISECONDS)
            .callTimeout(3_000, MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
