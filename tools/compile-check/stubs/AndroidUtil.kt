@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.util

import java.io.File

object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    fun isLoggable(tag: String?, level: Int): Boolean = false
    fun v(tag: String?, msg: String?): Int = 0
    fun d(tag: String?, msg: String?): Int = 0
    fun d(tag: String?, msg: String?, tr: Throwable?): Int = 0
    fun i(tag: String?, msg: String?): Int = 0
    fun i(tag: String?, msg: String?, tr: Throwable?): Int = 0
    fun w(tag: String?, msg: String?): Int = 0
    fun w(tag: String?, msg: String?, tr: Throwable?): Int = 0
    fun w(tag: String?, tr: Throwable?): Int = 0
    fun e(tag: String?, msg: String?): Int = 0
    fun e(tag: String?, msg: String?, tr: Throwable?): Int = 0
    fun e(tag: String?, msg: Throwable?): Int = 0
    fun println(priority: Int, tag: String?, msg: String?): Int = 0
}

object Base64 {
    const val NO_WRAP = 2
    const val DEFAULT = 0
    const val URL_SAFE = 8
    fun encodeToString(input: ByteArray, flags: Int): String = java.util.Base64.getEncoder().encodeToString(input)
    fun decode(input: String, flags: Int): ByteArray = java.util.Base64.getDecoder().decode(input)
    fun encode(input: ByteArray, flags: Int): ByteArray = java.util.Base64.getEncoder().encode(input)
}
