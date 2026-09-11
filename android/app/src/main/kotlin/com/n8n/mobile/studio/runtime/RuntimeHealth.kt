package com.n8n.mobile.studio.runtime

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Deliberately uses [HttpURLConnection] rather than an HTTP client: the only
 * requests this app ever makes are a few dozen bytes to its own process on
 * 127.0.0.1, and keeping the runtime core free of third-party dependencies means
 * the whole engine is unit-testable on a plain JVM.
 *
 * Loopback is enforced, not assumed: a non-loopback URL is refused outright, so a
 * misconfigured manifest can never make the app report "running" because some
 * remote server answered.
 */
class RuntimeHealth(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun probe(baseUrl: String, paths: List<String> = DEFAULT_PATHS): RuntimeStatus =
        withContext(Dispatchers.IO) { probeBlocking(baseUrl, paths) }

    fun probeBlocking(baseUrl: String, paths: List<String> = DEFAULT_PATHS): RuntimeStatus {
        if (!isLoopback(baseUrl)) {
            return RuntimeStatus(
                reachable = false,
                detail = "refused non-loopback endpoint $baseUrl",
                checkedAt = now(),
                url = baseUrl,
            )
        }
        var lastDetail = "no response from $baseUrl"
        paths.forEach { path ->
            val url = baseUrl.trimEnd('/') + if (path.startsWith("/")) path else "/$path"
            val started = now()
            val outcome = request(url)
            when (outcome) {
                is Outcome.Answered -> {
                    val elapsed = now() - started
                    if (outcome.code in 200..299 || outcome.code in 401..403) {
                        return RuntimeStatus(
                            reachable = true,
                            detail = "HTTP ${outcome.code} from $url",
                            httpCode = outcome.code,
                            latencyMs = elapsed,
                            checkedAt = now(),
                            url = url,
                        )
                    }
                    lastDetail = "HTTP ${outcome.code} from $url"
                }
                is Outcome.Failed -> lastDetail = outcome.detail
            }
        }
        return RuntimeStatus(reachable = false, detail = lastDetail, checkedAt = now(), url = baseUrl)
    }

    private fun request(url: String): Outcome = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "n8n-mobile-studio")
        }
        try {
            val code = connection.responseCode
            // Drain a bounded amount so the server can finish its response and the
            // socket is released back to the process instead of leaking.
            (connection.errorStream ?: connection.inputStream)?.use { stream ->
                val buffer = ByteArray(512)
                var total = 0
                while (total < 4_096) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                }
            }
            Outcome.Answered(code)
        } finally {
            connection.disconnect()
        }
    } catch (problem: Throwable) {
        Outcome.Failed("${problem.javaClass.simpleName}: ${problem.message ?: "no response"}")
    }

    private sealed interface Outcome {
        data class Answered(val code: Int) : Outcome
        data class Failed(val detail: String) : Outcome
    }

    companion object {
        val DEFAULT_PATHS: List<String> = listOf("/")

        const val DEFAULT_TIMEOUT_MS = 1_500

        /** True for 127.0.0.0/8, `localhost` and `::1` without any DNS lookup. */
        fun isLoopback(baseUrl: String): Boolean {
            val host = runCatching { URL(baseUrl).host }.getOrNull() ?: return false
            if (host.equals("localhost", ignoreCase = true)) return true
            val literal = host.removePrefix("[").removeSuffix("]")
            if (literal == "::1" || literal == "0:0:0:0:0:0:0:1") return true
            // Numeric IPv4 only: never resolve a name here (no DNS, no network).
            val parts = literal.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            return octets[0] == 127
        }
    }
}
