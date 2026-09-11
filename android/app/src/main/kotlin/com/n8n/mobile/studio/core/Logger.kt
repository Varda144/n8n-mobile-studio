package com.n8n.mobile.studio.core

import android.util.Log

/**
 * Minimal log wrapper that prefixes every tag with `N8NStudio-`.
 */
object Logger {
    const val TAG_PREFIX = "N8NStudio-"

    private fun tag(tag: String) = TAG_PREFIX + tag

    fun d(tag: String, msg: String, tr: Throwable? = null) =
        if (tr != null) Log.d(tag(tag), msg, tr) else Log.d(tag(tag), msg)

    fun i(tag: String, msg: String, tr: Throwable? = null) =
        if (tr != null) Log.i(tag(tag), msg, tr) else Log.i(tag(tag), msg)

    fun w(tag: String, msg: String, tr: Throwable? = null) =
        if (tr != null) Log.w(tag(tag), msg, tr) else Log.w(tag(tag), msg)

    fun e(tag: String, msg: String, tr: Throwable? = null) =
        if (tr != null) Log.e(tag(tag), msg, tr) else Log.e(tag(tag), msg)
}