package com.n8n.mobile.studio.opencode

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalResult
import java.io.File

/**
 * Facade over the local OpenCode runtime.
 *
 * Both of OpenCode's surfaces attach to the same server process owned by the
 * runtime service: the GUI views the loopback endpoint, and the terminal runs
 * commands against the same instance and the same project tree.
 */
class OpenCodeController(private val client: RuntimeClient?) {

    fun status(): EmbeddedComponentStatus =
        client?.statuses?.value?.get(EmbeddedComponent.OPENCODE)
            ?: EmbeddedComponentStatus(EmbeddedComponent.OPENCODE)

    val running: Boolean get() = status().state == EmbeddedProcessState.RUNNING

    suspend fun start(): AppResult<Unit> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.start(EmbeddedComponent.OPENCODE)
    }

    suspend fun stop(): AppResult<Unit> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.stop(EmbeddedComponent.OPENCODE)
    }

    suspend fun restart(): AppResult<Unit> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.restart(EmbeddedComponent.OPENCODE)
    }

    /** Projects the agent may read and write (app-owned, inside the sandbox). */
    fun projectsRoot(context: Context): File =
        File(RuntimePaths.forFilesDir(context.filesDir).projects, "default").apply { mkdirs() }

    /** Run an OpenCode command in the shared terminal, pointed at the runtime. */
    suspend fun run(
        sessionId: String,
        command: String,
        onEvent: (TerminalOutput) -> Unit,
    ): AppResult<TerminalResult> = AppResult.runSuspend {
        requireNotNull(client) { "runtime service is not connected" }
        client.touch(EmbeddedComponent.OPENCODE)
        client.runCommand(sessionId, command, onEvent).getOrThrow()
    }

    fun statusLine(): String = "${status().state.name.lowercase()} — ${status().message}"
}
