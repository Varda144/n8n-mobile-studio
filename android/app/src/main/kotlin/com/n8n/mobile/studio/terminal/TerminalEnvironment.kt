package com.n8n.mobile.studio.terminal

import java.io.File

/** Mutable set of environment variables handed to terminal processes. */
class TerminalEnvironment(base: Map<String, String> = emptyMap()) {
    var values: Map<String, String> = base
        private set

    fun with(key: String, value: String): TerminalEnvironment {
        values = values + (key to value)
        return this
    }

    /** Populate the bare-minimum env a native runtime expects. */
    fun withDefaultRuntimeEnv(
        homeDir: File,
        projectDir: File,
        pathExtra: String? = null,
    ): TerminalEnvironment {
        values = values + mapOf(
            "HOME" to homeDir.absolutePath,
            "TMPDIR" to File(homeDir, "tmp").absolutePath,
            "PWD" to projectDir.absolutePath,
        )
        if (values["PATH"] == null) {
            with("PATH", pathExtra ?: "/system/bin:/vendor/bin:/system/xbin:/usr/bin")
        }
        return this
    }
}