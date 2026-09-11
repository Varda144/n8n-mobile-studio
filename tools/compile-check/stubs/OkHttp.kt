@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")

package okhttp3

import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Offline check stub for the OkHttp surface used by the app's REST helpers
 * (`data/api`, the n8n API client). Signatures only — the runtime core itself
 * speaks plain HttpURLConnection so it needs none of this.
 */
class MediaType(val type: String) {
    companion object {
        fun parse(value: String): MediaType? = MediaType(value)
        val JSON: MediaType = MediaType("application/json")
    }

    override fun toString(): String = type
}

class RequestBody internal constructor(val content: String, val mediaType: MediaType?) {
    companion object {
        fun create(mediaType: MediaType?, content: String): RequestBody = RequestBody(content, mediaType)
        fun create(content: String): RequestBody = RequestBody(content, MediaType.JSON)
        val EMPTY: RequestBody = RequestBody("", null)

        /**
         * OkHttp 5 declares these as member extensions of the companion object,
         * which is why call sites import
         * `okhttp3.RequestBody.Companion.toRequestBody`.
         */
        fun String.toRequestBody(contentType: MediaType? = null): RequestBody =
            create(contentType ?: MediaType.JSON, this)

        fun ByteArray.toRequestBody(contentType: MediaType? = null): RequestBody =
            create(contentType ?: MediaType.JSON, String(this))
    }
}

class Request private constructor(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: RequestBody?,
) {
    class Builder(private var url: String = "") {
        private var method = "GET"
        private val headers = LinkedHashMap<String, String>()
        private var body: RequestBody? = null

        fun url(url: String): Builder = apply { this.url = url }
        fun get(): Builder = apply { method = "GET" }
        fun post(body: RequestBody): Builder = apply { method = "POST"; this.body = body }
        fun put(body: RequestBody): Builder = apply { method = "PUT"; this.body = body }
        fun patch(body: RequestBody): Builder = apply { method = "PATCH"; this.body = body }
        fun delete(body: RequestBody? = null): Builder = apply { method = "DELETE"; this.body = body }
        fun header(name: String, value: String): Builder = apply { headers[name] = value }
        fun addHeader(name: String, value: String): Builder = apply { headers[name] = value }
        fun build(): Request = Request(url, method, headers, body)
    }
}

class Response internal constructor(val code: Int, val message: String, private val payload: String) : AutoCloseable {
    val isSuccessful: Boolean get() = code in 200..299

    /** OkHttp 5 exposes `body` as a property (was `body()` in 4.x). */
    val body: ResponseBody get() = ResponseBody(payload)

    override fun close() {}
}

class ResponseBody(private val payload: String) {
    fun string(): String = payload
    fun byteStream(): InputStream = payload.byteInputStream()
    fun contentType(): MediaType? = MediaType.JSON
    fun close() {}
}

class Call internal constructor(private val request: Request, private val responder: (Request) -> Response) {
    fun execute(): Response = responder(request)
    fun cancel() {}
    fun isCanceled(): Boolean = false
}

class OkHttpClient internal constructor(
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long,
    val writeTimeoutMs: Long,
    val callTimeoutMs: Long,
    val retryOnConnectionFailure: Boolean,
) {
    private val responder: (Request) -> Response = { Response(200, "OK", "{}") }

    constructor() : this(10_000, 10_000, 10_000, 0, true)

    fun newCall(request: Request): Call = Call(request, responder)

    class Builder {
        private var connect = 10_000L
        private var read = 10_000L
        private var write = 10_000L
        private var call = 0L
        private var retry = true

        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = apply { connect = unit.toMillis(timeout) }
        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = apply { read = unit.toMillis(timeout) }
        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = apply { write = unit.toMillis(timeout) }
        fun callTimeout(timeout: Long, unit: TimeUnit): Builder = apply { call = unit.toMillis(timeout) }
        fun retryOnConnectionFailure(value: Boolean): Builder = apply { retry = value }
        fun build(): OkHttpClient = OkHttpClient(connect, read, write, call, retry)
    }
}

object HttpUrl {
    fun get(url: String): Any = url
}

/** `RequestBody.create(...)` companion helpers are declared above. */
