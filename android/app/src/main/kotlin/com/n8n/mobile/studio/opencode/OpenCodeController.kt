package com.n8n.mobile.studio.opencode

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.opencode.OpenCodeRuntime
import com.n8n.mobile.studio.terminal.TerminalCommand
import com.n8n.mobile.studio.terminal.TerminalEngine
import com.n8n.mobile.studio.terminal.TerminalEnvironment
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Coordinates the embedded OpenCode runtime and its terminal front-end. */
class OpenCodeController(
    private val runtime: OpenCodeRuntime = OpenCodeRuntime(),
    private val engine: TerminalEngine = TerminalEngine(),
    private val gui: OpenCodeGuiState = OpenCodeGuiState(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: OpenCodeGuiState get() = gui

    suspend fun start(context: Context): AppResult<EmbeddedComponentStatus> =
        AppResult.runSuspend {
            gui.markStarting()
            val status = runtime.start(context).getOrThrow()
            if (status.state == EmbeddedProcessState.RUNNING) {
                val pid = status.pid ?: -1
                gui.updateSession(
                    OpenCodeSession(
                        id = SESSION_ID,
                        cwd = RuntimePaths.home(context).absolutePath,
                        running = true,
                        pid = pid,
                    ),
                )
                gui.markRunning(pid)
            } else {
                gui.markStopped()
            }
            status
        }

    suspend fun stop(): AppResult<Unit> = AppResult.runSuspend {
        runtime.stop().getOrThrow()
        gui.markStopped()
    }

    fun status(): EmbeddedComponentStatus = runtime.status()

    fun beginTerminal(context: Context, projectDir: File): AppResult<TerminalSession> =
        AppResult.run {
            val environment = TerminalEnvironment()
                .withDefaultRuntimeEnv(RuntimePaths.home(context), projectDir)
            val command = TerminalCommand(
                executable = resolveExecutable(context),
                cwd = projectDir,
                environment = environment.values,
            )
            gui.updateSession(
                OpenCodeSession(
                    id = SESSION_ID,
                    cwd = projectDir.absolutePath,
                    running = true,
                    pid = null,
                ),
            )
            scope.launch {
                engine.run(command, environment) { event -> gui.onEvent(event) }
            }
            TerminalSession(id = SESSION_ID, command = command)
        }

    /** Forward an external event (e.g. from the bridge) into the GUI state. */
    fun feed(event: TerminalOutput) = gui.onEvent(event)

    private fun resolveExecutable(context: Context): String {
        val packaged = File(RuntimePaths.bin(context), "opencode")
        return if (packaged.exists()) packaged.absolutePath else "opencode"
    }

    private companion object {
        const val SESSION_ID = "opencode"
    }
}