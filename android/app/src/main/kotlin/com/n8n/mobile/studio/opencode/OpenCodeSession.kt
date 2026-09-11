package com.n8n.mobile.studio.opencode

/** Snapshot of the embedded OpenCode session. */
data class OpenCodeSession(
    val id: String,
    val cwd: String,
    val running: Boolean,
    val pid: Long?,
)