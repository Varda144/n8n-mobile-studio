package com.n8n.mobile.studio.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal log wrapper that prefixes every tag with `N8NStudio-`.
 *
 * Records are additionally handed to an in-memory sink so the Settings screen
 * can show recent app-level diagnostics on device without adb.
 */
object Logger {
    const val TAG_PREFIX = "N8NStudio-"

    private val sink = CopyOnWriteArrayList<Record>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class Record(val level: String, val tag: String, val message: String, val at: Long)

    private fun tag(tag: String) = TAG_PREFIX + tag

    private fun record(level: String, tag: String, msg: String) {
        sink.add(Record(level, tag, msg, System.currentTimeMillis()))
        while (sink.size > MAX_RECORDS) {
            sink.removeAt(0)
        }
    }

    fun recent(limit: Int = 200): List<String> = sink.takeLast(limit).map {
        "${formatter.format(Date(it.at))} ${it.level}/${it.tag} ${it.message}"
    }

    fun clearSink() = sink.clear()

    fun d(tag: String, msg: String, tr: Throwable? = null) {
        record("D", tag, msg)
        if (tr != null) android.util.Log.d(tag(tag), msg, tr) else android.util.Log.d(tag(tag), msg)
    }

    fun i(tag: String, msg: String, tr: Throwable? = null) {
        record("I", tag, msg)
        if (tr != null) android.util.Log.i(tag(tag), msg, tr) else android.util.Log.i(tag(tag), msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        record("W", tag, msg)
        if (tr != null) android.util.Log.w(tag(tag), msg, tr) else android.util.Log.w(tag(tag), msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        record("E", tag, msg)
        if (tr != null) android.util.Log.e(tag(tag), msg, tr) else android.util.Log.e(tag(tag), msg)
    }

    private const val MAX_RECORDS = 500
}
