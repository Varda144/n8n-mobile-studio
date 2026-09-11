package com.n8n.mobile.studio.opencode

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.terminal.TerminalCommand
import com.n8n.mobile.studio.terminal.TerminalEngine
import com.n8n.mobile.studio.terminal.TerminalEnvironment
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalSession
import java.io.File

/** Bridges external terminal events into the OpenCode GUI state. */
class OpenCodeTerminalBridge(
    private val engine: TerminalEngine,
    private val gui: OpenCodeGuiState = OpenCodeGuiState(),
) {
    suspend fun launch(context: Context, projectDir: File): AppResult<TerminalSession> =
        AppResult.runSuspend {
            val executable = File(RuntimePaths.bin(context), "opencode")
            if (!executable.exists()) {
                throw IllegalStateException("OpenCode runtime not packaged")
            }
            val environment = TerminalEnvironment()
                .withDefaultRuntimeEnv(RuntimePaths.home(context), projectDir)
            val command = TerminalCommand(
                executable = executable.absolutePath,
                cwd = projectDir,
                environment = environment.values,
            )
            engine.run(command, environment) { event -> gui.onEvent(event) }.getOrThrow()
        }

    fun send(event: TerminalOutput) = gui.onEvent(event)

    fun clear() = gui.clear()
}