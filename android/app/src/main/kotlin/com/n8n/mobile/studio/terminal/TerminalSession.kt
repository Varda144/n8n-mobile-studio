package com.n8n.mobile.studio.terminal

import java.util.UUID

/**
 * A terminal front-end session.
 *
 * One session = one [TerminalState] (working directory + exit code) plus the
 * bounded output buffer the UI renders. Runtime processes are *not* owned by the
 * session: they belong to the foreground service, so closing the terminal or
 * leaving the app never stops n8n or OpenCode.
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    root: java.io.File,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val output: TerminalOutputBuffer = TerminalOutputBuffer(),
) {
    val state: TerminalState = TerminalState(root)

    var running: Boolean = true
        private set

    fun finish() {
        running = false
    }

    fun prompt(): String = "${state.displayCwd()} $"
}
