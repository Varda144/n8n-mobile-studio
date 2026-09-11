package com.n8n.mobile.studio.runtime

import android.content.Context

/**
 * Identifies the embedded runtime component.
 */
enum class EmbeddedComponent {
    N8N,
    OPENCODE,
}

/**
 * Lifecycle state of an [EmbeddedRuntime] process.
 */
enum class EmbeddedProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

/**
 * Snapshot of a single component's runtime status.
 */
data class EmbeddedComponentStatus(
    val component: EmbeddedComponent,
    val state: EmbeddedProcessState = EmbeddedProcessState.STOPPED,
    val pid: Long? = null,
    val endpoint: String? = null,
    val message: String = "Stopped",
)

/**
 * Contract for a startable/stoppable embedded runtime component.
 */
interface EmbeddedRuntime {
    val component: EmbeddedComponent

    /** Ensure the component binaries are unpacked and ready to launch. */
    suspend fun prepare(context: Context): Result<Unit>

    /** Start the process and return its live status. */
    suspend fun start(context: Context): Result<EmbeddedComponentStatus>

    /** Gracefully stop the process. */
    suspend fun stop(): Result<Unit>

    /** Return the last known status without side-effects. */
    fun status(): EmbeddedComponentStatus
}