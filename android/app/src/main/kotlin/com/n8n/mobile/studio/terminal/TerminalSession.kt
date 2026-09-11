package com.n8n.mobile.studio.terminal

/** Handle for a live (or already finished) terminal session. */
class TerminalSession(
    val id: String,
    val command: TerminalCommand,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val output: TerminalOutputBuffer = TerminalOutputBuffer(),
) {
    var running: Boolean = true
        private set

    fun finish() {
        running = false
    }
}