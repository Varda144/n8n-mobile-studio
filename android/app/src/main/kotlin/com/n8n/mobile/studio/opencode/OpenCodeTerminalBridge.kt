package com.n8n.mobile.studio.opencode

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalResult
import java.io.File

/**
 * Routes terminal input for OpenCode into the local runtime service.
 *
 * The bridge exists so the OpenCode screens and the shared terminal tab issue the
 * identical call (`client.runCommand`); there is no second command path, and no
 * code here can start a process the supervisor does not know about.
 */
class OpenCodeTerminalBridge(
    private val client: RuntimeClient?,
    private val controller: OpenCodeController = OpenCodeController(client),
) {

    suspend fun run(
        command: String,
        sessionId: String = OpenCodeSession.DEFAULT_ID,
        onEvent: (TerminalOutput) -> Unit,
    ): AppResult<TerminalResult> = controller.run(sessionId, command, onEvent)

    /** Convenience for the common actions, all routed through the terminal engine. */
    suspend fun startServer(
        sessionId: String = OpenCodeSession.DEFAULT_ID,
        onEvent: (TerminalOutput) -> Unit,
    ): AppResult<TerminalResult> = run("opencode start", sessionId, onEvent)

    suspend fun status(
        sessionId: String = OpenCodeSession.DEFAULT_ID,
        onEvent: (TerminalOutput) -> Unit,
    ): AppResult<TerminalResult> = run("opencode status", sessionId, onEvent)

    suspend fun logs(
        sessionId: String = OpenCodeSession.DEFAULT_ID,
        lines: Int = 60,
        onEvent: (TerminalOutput) -> Unit,
    ): AppResult<TerminalResult> = run("opencode logs $lines", sessionId, onEvent)

    fun projectsRoot(context: Context): File = controller.projectsRoot(context)
}
