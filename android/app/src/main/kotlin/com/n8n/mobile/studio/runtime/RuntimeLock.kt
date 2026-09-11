package com.n8n.mobile.studio.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes access to runtime lifecycle operations.
 */
class RuntimeLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock(action = block)
}