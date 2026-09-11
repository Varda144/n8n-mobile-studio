package kotlinx.serialization.json

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlinx.serialization.JsonCodec

/** Minimal reflective JSON codec used by the offline check harness. */
internal class JsonCodecImpl(private val json: Json) : JsonCodec {

    private val ignoreUnknown: Boolean get() = json.config.ignoreUnknownKeys
    private val explicitNulls: Boolean get() = json.config.explicitNulls

    override fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    override fun <T> decode(text: String, type: Class<T>): T {
        val parsed = JsonParser(text).parseValue()
        @Suppress("UNCHECKED_CAST")
        return read(parsed, type) as T
    }

    fun decode(text: String, type: java.lang.reflect.Type): Any? = read(JsonParser(text).parseValue(), type)

    // ---------------------------------------------------------------- encoding

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value.toString())
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double -> out.append(value.toString())
            is Float -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                value.forEach { (key, entry) ->
                    if (entry == null && !explicitNulls) return@forEach
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    write(out, entry)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { index, entry ->
                    if (index > 0) out.append(',')
                    write(out, entry)
                }
                out.append(']')
            }
            else -> writeObject(out, value)
        }
    }

    private fun writeObject(out: StringBuilder, value: Any) {
        out.append('{')
        var first = true
        fieldsOf(value.javaClass).forEach { field ->
            val entry = field.get(value)
            val isDefaultZero = entry == null || (entry is Number && entry.toLong() == 0L) ||
                (entry is String && entry.isEmpty()) || entry == false || (entry is Map<*, *> && entry.isEmpty()) ||
                (entry is Iterable<*> && !entry.iterator().hasNext())
            if (isDefaultZero && !json.config.encodeDefaults) return@forEach
            if (!first) out.append(',')
            first = false
            writeString(out, field.name)
            out.append(':')
            write(out, entry)
        }
        out.append('}')
    }

    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        text.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    // ---------------------------------------------------------------- decoding

    /**
     * Decode a JSON value into [type].
     *
     * Generic types matter here: `Map<String, RuntimeEntry>` must decode its
     * values into RuntimeEntry, which a raw Class cannot express, so field types
     * come from [java.lang.reflect.Field.genericType].
     */
    private fun read(value: Any?, type: Type): Any? {
        if (value == null) return null
        if (type is ParameterizedType) {
            val raw = type.rawType as Class<*>
            val arguments = type.actualTypeArguments
            return when {
                Map::class.java.isAssignableFrom(raw) -> {
                    val valueType = arguments.getOrNull(1) ?: Any::class.java
                    (value as Map<*, *>).mapValues { (_, entry) -> read(entry, valueType) }
                }
                Iterable::class.java.isAssignableFrom(raw) -> {
                    val elementType = arguments.getOrNull(0) ?: Any::class.java
                    (value as List<*>).map { read(it, elementType) }
                }
                else -> read(value, raw)
            }
        }
        val clazz = type as? Class<*> ?: Any::class.java
        return when {
            clazz == String::class.java -> value.toString()
            clazz == Int::class.javaPrimitiveType || clazz == Int::class.java -> (value as Number).toInt()
            clazz == Long::class.javaPrimitiveType || clazz == Long::class.java -> (value as Number).toLong()
            clazz == Boolean::class.javaPrimitiveType || clazz == Boolean::class.java -> value as Boolean
            clazz == Double::class.javaPrimitiveType || clazz == Double::class.java -> (value as Number).toDouble()
            clazz == Float::class.javaPrimitiveType || clazz == Float::class.java -> (value as Number).toFloat()
            clazz == Any::class.java -> value
            Map::class.java.isAssignableFrom(clazz) -> (value as Map<*, *>).mapValues { (_, entry) -> read(entry, Any::class.java) }
            Iterable::class.java.isAssignableFrom(clazz) -> (value as List<*>).map { read(it, Any::class.java) }
            else -> readObject(value as Map<*, *>, clazz)
        }
    }

    private fun readObject(source: Map<*, *>, type: Class<*>): Any {
        val fields = fieldsOf(type)
        val constructor = constructorOf(type, fields.size)
            ?: error("no ${fields.size}-argument constructor on ${type.name}")
        val args = arrayOfNulls<Any?>(fields.size + 2)
        var mask = 0
        fields.forEachIndexed { index, field ->
            if (source.containsKey(field.name)) {
                args[index] = read(source[field.name], field.genericType)
            } else {
                mask = mask or (1 shl index)
                // A masked argument is ignored by Kotlin's synthetic default
                // constructor, but reflection still has to hand over a value of
                // the right type: null cannot be unboxed into a primitive.
                args[index] = zeroOf(field.type)
            }
        }
        args[fields.size] = mask
        args[fields.size + 1] = null // synthetic DefaultConstructorMarker parameter
        return constructor.newInstance(*args)
    }

    private fun zeroOf(type: Class<*>): Any? = when (type) {
        Int::class.javaPrimitiveType, Int::class.java -> 0
        Long::class.javaPrimitiveType, Long::class.java -> 0L
        Boolean::class.javaPrimitiveType, Boolean::class.java -> false
        Double::class.javaPrimitiveType, Double::class.java -> 0.0
        Float::class.javaPrimitiveType, Float::class.java -> 0.0f
        else -> null
    }

    private fun constructorOf(type: Class<*>, fieldCount: Int): Constructor<*>? =
        type.declaredConstructors.firstOrNull { it.parameterCount == fieldCount + 2 } ?: null

    /**
     * Fields in declaration order, which is the order of the primary-constructor
     * parameters for a Kotlin data class. `Class.getDeclaredFields()` is not
     * specified to preserve declaration order, but HotSpot does; this harness only
     * needs to be right on the JVMs used for local checks and CI.
     */
    private fun fieldsOf(type: Class<*>): List<Field> =
        type.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .onEach { it.isAccessible = true }
}

/** Recursive-descent parser for the JSON subset the app writes and reads. */
internal class JsonParser(private val text: String) {
    private var index = 0

    fun parseValue(): Any? {
        skipWhitespace()
        if (index >= text.length) error("unexpected end of JSON")
        return when (val char = text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> if (char == '-' || char.isDigit()) parseNumber() else error("unexpected '$char' at $index")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') { index++; return result }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            result[key] = parseValue()
            skipWhitespace()
            when (val char = next()) {
                ',' -> continue
                '}' -> return result
                else -> error("unexpected '$char' in object at $index")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val result = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') { index++; return result }
        while (true) {
            result.add(parseValue())
            skipWhitespace()
            when (val char = next()) {
                ',' -> continue
                ']' -> return result
                else -> error("unexpected '$char' in array at $index")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            val char = next()
            when (char) {
                '"' -> return out.toString()
                '\\' -> when (val escape = next()) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        val hex = text.substring(index, index + 4)
                        index += 4
                        out.append(hex.toInt(16).toChar())
                    }
                    else -> error("bad escape \\$escape")
                }
                else -> out.append(char)
            }
        }
    }

    private fun parseNumber(): Any {
        val start = index
        while (index < text.length && (text[index].isDigit() || text[index] in "-+.eE")) index++
        val raw = text.substring(start, index)
        return raw.toLongOrNull() ?: raw.toDouble()
    }

    private fun <T> parseLiteral(literal: String, value: T): T {
        if (!text.startsWith(literal, index)) error("expected $literal at $index")
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun peek(): Char? = text.getOrNull(index)

    private fun next(): Char = text[index++]

    private fun expect(char: Char) {
        val actual = next()
        if (actual != char) error("expected '$char' but found '$actual' at ${index - 1}")
    }
}
