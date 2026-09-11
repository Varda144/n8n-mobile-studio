package com.n8n.mobile.studio.terminal

import java.io.File

/**
 * A command line that has been tokenised but not yet executed.
 *
 * The terminal never uses `/system/bin/sh`: a shell would both escape the app's
 * sandbox and be unavailable on locked-down devices. Instead the studio ships a
 * small, explicit command set (see [TerminalEngine]) with unix-like semantics
 * over the app-owned runtime directory, so `ls`, `cd`, `cat`, `n8n start` … are
 * real operations on real files and real processes.
 */
data class ParsedCommand(
    val name: String,
    val args: List<String>,
    val raw: String,
) {
    val displayName: String get() = (listOf(name) + args).joinToString(" ")

    val flags: Set<String> get() = args.filter { it.startsWith("-") }.flatMap { token ->
        if (token.startsWith("--")) listOf(token) else token.drop(1).map { "-$it" }
    }.toSet()

    val operands: List<String> get() = args.filterNot { it.startsWith("-") }
}

/** Exit status plus the lines the command produced. */
data class TerminalResult(
    val exitCode: Int,
    val lines: List<String> = emptyList(),
) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Session state carried between commands: the working directory and the last
 * exit code, mirroring what a shell session would remember.
 */
class TerminalState(
    val root: File,
    initialCwd: File = root,
) {
    var cwd: File = initialCwd
        private set

    var lastExitCode: Int = 0
        private set

    fun cd(target: File) {
        cwd = target
    }

    fun record(result: TerminalResult) {
        lastExitCode = result.exitCode
    }

    /** `~/projects` style display path used by the prompt. */
    fun displayCwd(): String {
        val rootPath = root.absolutePath
        return if (cwd.absolutePath.startsWith(rootPath)) {
            "~" + cwd.absolutePath.removePrefix(rootPath)
        } else {
            cwd.absolutePath
        }
    }
}
