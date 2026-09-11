package com.n8n.mobile.studio.local.runtime

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates the two supported embedded components and keeps lifecycle serialized. */
class LocalRuntimeManager(
    private val n8n: EmbeddedRuntime = N8nEmbeddedRuntime(),
    private val openCode: EmbeddedRuntime = OpenCodeEmbeddedRuntime(),
) {
    private val mutex = Mutex()

    suspend fun prepare(context: Context): Result<Unit> = mutex.withLock {
        runCatching {
            ensureRuntimeDirectories(context).getOrThrow()
            n8n.prepare(context).getOrThrow()
            openCode.prepare(context).getOrThrow()
        }
    }

    suspend fun start(context: Context, component: EmbeddedComponent): Result<EmbeddedComponentStatus> = mutex.withLock {
        runtime(component).start(context)
    }

    suspend fun stop(component: EmbeddedComponent): Result<Unit> = mutex.withLock {
        runtime(component).stop()
    }

    fun status(component: EmbeddedComponent): EmbeddedComponentStatus = runtime(component).status()

    fun allStatuses(): List<EmbeddedComponentStatus> = listOf(n8n.status(), openCode.status())

    private fun runtime(component: EmbeddedComponent): EmbeddedRuntime = when (component) {
        EmbeddedComponent.N8N -> n8n
        EmbeddedComponent.OPENCODE -> openCode
    }
}

object LocalRuntimeLocator {
    fun endpoint(component: EmbeddedComponent): String? = when (component) {
        EmbeddedComponent.N8N -> N8nEmbeddedRuntime.ENDPOINT
        EmbeddedComponent.OPENCODE -> null
    }
}
