package kotlinx.serialization.json

import kotlinx.serialization.JsonCodec
import kotlinx.serialization.StringFormat

/**
 * Offline check stub. Mirrors the small slice of the kotlinx.serialization API
 * that this project's runtime core uses, backed by a reflective JSON codec so the
 * unit tests can run without Maven. Never shipped in the APK — Gradle always
 * compiles against the real library.
 */
class JsonBuilder {
    var ignoreUnknownKeys: Boolean = false
    var isLenient: Boolean = false
    var explicitNulls: Boolean = true
    var encodeDefaults: Boolean = false
    var prettyPrint: Boolean = false
}

open class Json internal constructor(internal val config: JsonBuilder) : StringFormat, JsonCodec {

    private val codec: JsonCodec = JsonCodecImpl(this)

    companion object {
        val Default: Json = Json(JsonBuilder())
    }

    fun with(builderAction: JsonBuilder.() -> Unit): Json {
        val copy = JsonBuilder().also { target ->
            target.ignoreUnknownKeys = config.ignoreUnknownKeys
            target.isLenient = config.isLenient
            target.explicitNulls = config.explicitNulls
            target.encodeDefaults = config.encodeDefaults
            target.prettyPrint = config.prettyPrint
            target.builderAction()
        }
        return Json(copy)
    }

    override fun <T> decode(text: String, type: Class<T>): T = codec.decode(text, type)

    override fun encode(value: Any?): String = codec.encode(value)
}

fun Json(from: Json = Json.Default, builderAction: JsonBuilder.() -> Unit): Json = from.with(builderAction)
