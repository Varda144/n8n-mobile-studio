package kotlinx.serialization

/**
 * Offline check stub for the kotlinx.serialization surface used by the runtime
 * core. The encoder/decoder is a compact reflective implementation: enough to
 * round-trip the plain data classes in this project (strings, numbers, booleans,
 * lists, maps and nested classes) so unit tests can exercise real code paths.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Serializable

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class SerialName(val value: String)

interface StringFormat

inline fun <reified T> StringFormat.decodeFromString(string: String): T =
    (this as JsonCodec).decode(string, T::class.java)

inline fun <reified T> StringFormat.encodeToString(value: T): String =
    (this as JsonCodec).encode(value)

interface JsonCodec {
    fun <T> decode(text: String, type: Class<T>): T
    fun encode(value: Any?): String
}
