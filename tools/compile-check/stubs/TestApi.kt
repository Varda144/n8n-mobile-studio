@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch")

package org.junit

/**
 * Offline check stub: the JUnit4 surface the unit tests use, implemented by the
 * throwaway runner in `tools/compile-check/TestRunner.kt`. CI runs the real JUnit4
 * through Gradle; this exists so tests can be executed on a machine with no Maven
 * access at all.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test(val expected: kotlin.reflect.KClass<out Throwable> = Throwable::class)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Before

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class After

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Ignore(val value: String = "")

object Assert {
    fun assertTrue(message: String?, condition: Boolean) = kotlin.test.assertTrue(condition, message ?: "")
    fun assertTrue(condition: Boolean) = kotlin.test.assertTrue(condition)
    fun assertFalse(message: String?, condition: Boolean) = kotlin.test.assertFalse(condition, message ?: "")
    fun assertFalse(condition: Boolean) = kotlin.test.assertFalse(condition)
    fun assertEquals(expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual)
    fun assertEquals(message: String?, expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual, message ?: "")
    fun assertNotEquals(unexpected: Any?, actual: Any?) = kotlin.test.assertNotEquals(unexpected, actual)
    fun assertNull(actual: Any?) = kotlin.test.assertNull(actual)
    fun assertNotNull(actual: Any?) = kotlin.test.assertNotNull(actual)
    fun assertNotNull(message: String?, actual: Any?) = kotlin.test.assertNotNull(actual, message ?: "")
    fun fail(message: String?): Nothing = kotlin.test.fail(message ?: "failed")
}
