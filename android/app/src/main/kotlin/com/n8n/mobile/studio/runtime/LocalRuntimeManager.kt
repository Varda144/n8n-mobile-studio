package com.n8n.mobile.studio.runtime

import android.content.Context
import com.n8n.mobile.studio.runtime.n8n.N8nRuntime
import com.n8n.mobile.studio.runtime.opencode.OpenCodeRuntime

/**
 * Orchestrates the embedded N8N and OpenCode runtimes behind a single lock.
 */
class LocalRuntimeManager(
    private val n8n: EmbeddedRuntime = N8nRuntime(),
    private val openCode: EmbeddedRuntime = OpenCodeRuntime(),
) {
    private val lock = RuntimeLock()

    /**
     * Ensure the runtime directory tree exists and both components are unpacked.
     */
    suspend fun prepare(context: Context): Result<Unit> = lock.withLock {
        RuntimePaths.ensure(context)
            .andThen { n8n.prepare(context) }
            .andThen { openCode.prepare(context) }
    }

    suspend fun start(context: Context, component: EmbeddedComponent): Result<EmbeddedComponentStatus> = lock.withLock {
        runtime(component).start(context)
    }

    suspend fun stop(component: EmbeddedComponent): Result<Unit> = lock.withLock {
        runtime(component).stop()
    }

    fun status(component: EmbeddedComponent): EmbeddedComponentStatus = runtime(component).status()

    fun allStatuses(): List<EmbeddedComponentStatus> = listOf(n8n.status(), openCode.status())

    private fun runtime(component: EmbeddedComponent): EmbeddedRuntime = when (component) {
        EmbeddedComponent.N8N -> n8n
        EmbeddedComponent.OPENCODE -> openCode
    }

    private inline fun <T, R> Result<T>.andThen(block: (T) -> Result<R>): Result<R> =
        fold(onSuccess = block, onFailure = { Result.failure(it) })
}

/**
 * Resolves a component's public endpoint, if any.
 */
object LocalRuntimeLocator {
    fun endpoint(component: EmbeddedComponent): String? = when (component) {
        EmbeddedComponent.N8N -> "http://127.0.0.1:5678"
        EmbeddedComponent.OPENCODE -> null
    }
}