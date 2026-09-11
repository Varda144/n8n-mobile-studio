package com.n8n.mobile.studio.terminal

import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import java.io.File

/**
 * Environment handed to anything the terminal launches.
 *
 * The same [RuntimePaths] and [NodeRuntime] values the supervisor uses are
 * reused, so a program started from the terminal sees the identical HOME, PATH
 * and sandbox layout as the n8n/OpenCode processes.
 */
class TerminalEnvironment(
    private val paths: RuntimePaths,
    private val node: NodeRuntime,
) {

    fun forComponent(
        component: EmbeddedComponent,
        heapMb: Int,
        workingDir: File,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> = node.nodeEnv(component, heapMb, workingDir, extra)

    /** Directories that may be listed/traversed from the terminal. */
    fun root(): File = paths.root

    fun projects(): File = paths.projects

    fun bin(): File = paths.bin
}
