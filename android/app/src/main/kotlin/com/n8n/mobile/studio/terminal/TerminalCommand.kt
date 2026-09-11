package com.n8n.mobile.studio.terminal

import java.io.File

/** A single command to run inside a terminal session. */
data class TerminalCommand(
    val executable: String,
    val args: List<String> = emptyList(),
    val cwd: File? = null,
    val environment: Map<String, String> = emptyMap(),
) {
    val displayName: String get() = "$executable ${args.joinToString(" ")}".trim()
}