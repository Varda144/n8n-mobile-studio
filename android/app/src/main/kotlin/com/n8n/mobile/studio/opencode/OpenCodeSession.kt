package com.n8n.mobile.studio.opencode

import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus

/**
 * Snapshot of the local OpenCode session as the UI presents it.
 *
 * There is one server process per device; "session" here means the front-end
 * session (GUI view or terminal), not a second runtime.
 */
data class OpenCodeSession(
    val id: String = DEFAULT_ID,
    val terminalSessionId: String = "opencode-terminal",
    val endpoint: String? = null,
    val state: String = "not installed",
    val pid: Long? = null,
    val version: String? = null,
    val projectsDir: String = "",
) {
    val running: Boolean get() = state == "running"

    companion object {
        const val DEFAULT_ID = "opencode-main"

        fun from(status: EmbeddedComponentStatus, projectsDir: String): OpenCodeSession =
            OpenCodeSession(
                endpoint = status.endpoint,
                state = status.state.name.lowercase(),
                pid = status.pid,
                version = status.version,
                projectsDir = projectsDir,
            )
    }
}
