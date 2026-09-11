@file:Suppress("PackageDirectoryMismatch")

package kotlin.test

/**
 * Offline check stub for `kotlin.test` assertions (see TestApi.kt). The real
 * kotlin-test artifacts are compiled by Gradle in CI.
 */
class AssertionError(message: String) : java.lang.AssertionError(message)

fun fail(message: String? = null): Nothing = throw AssertionError(message ?: "assertion failed")

fun assertTrue(actual: Boolean, message: String? = null) {
    if (!actual) fail(message ?: "expected true")
}

fun assertFalse(actual: Boolean, message: String? = null) {
    if (actual) fail(message ?: "expected false")
}

fun assertEquals(expected: Any?, actual: Any?, message: String? = null) {
    if (expected != actual) fail(message ?: "expected <$expected> but was <$actual>")
}

fun assertNotEquals(illegal: Any?, actual: Any?, message: String? = null) {
    if (illegal == actual) fail(message ?: "expected not <$illegal>")
}

fun assertNull(actual: Any?, message: String? = null) {
    if (actual != null) fail(message ?: "expected null but was <$actual>")
}

fun <T : Any> assertNotNull(actual: T?, message: String? = null): T =
    actual ?: throw AssertionError(message ?: "expected not null")

fun assertSame(expected: Any?, actual: Any?, message: String? = null) {
    if (expected !== actual) fail(message ?: "expected same instance")
}

inline fun <reified T : Throwable> assertFailsWith(message: String? = null, block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError(message ?: "expected ${T::class.simpleName} but was ${error::class.simpleName}")
    }
    throw AssertionError(message ?: "expected ${T::class.simpleName} to be thrown")
}

fun assertContains(iterable: Iterable<*>, element: Any?, message: String? = null) {
    if (!iterable.contains(element)) fail(message ?: "expected collection to contain <$element>")
}

fun assertContains(sequence: CharSequence, other: CharSequence, ignoreCase: Boolean = false, message: String? = null) {
    if (!sequence.toString().contains(other, ignoreCase)) fail(message ?: "expected <$sequence> to contain <$other>")
}
