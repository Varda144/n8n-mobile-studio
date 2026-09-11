package com.n8n.mobile.studio.local.runtime

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Common contract for software that is owned by the APK's private runtime.
 * No Termux or external server is required by this abstraction.
 */
enum class EmbeddedComponent {
    N8N,
    OPENCODE,
}

enum class EmbeddedProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

data class EmbeddedComponentStatus(
    val component: EmbeddedComponent,
    val state: EmbeddedProcessState = EmbeddedProcessState.STOPPED,
    val pid: Long? = null,
    val endpoint: String? = null,
    val message: String = "Stopped",
)

interface EmbeddedRuntime {
    val component: EmbeddedComponent
    suspend fun prepare(context: Context): Result<Unit>
    suspend fun start(context: Context): Result<EmbeddedComponentStatus>
    suspend fun stop(): Result<Unit>
    fun status(): EmbeddedComponentStatus
}

internal fun runtimeRoot(context: Context): File = File(context.filesDir, "local-runtime")

internal suspend fun ensureRuntimeDirectories(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        runtimeRoot(context).resolve("bin").mkdirs()
        runtimeRoot(context).resolve("home").mkdirs()
        runtimeRoot(context).resolve("projects").mkdirs()
        runtimeRoot(context).resolve("logs").mkdirs()
        runtimeRoot(context).resolve("data").mkdirs()
        runtimeRoot(context).resolve("tmp").mkdirs()
    }
}
