package com.n8n.mobile.studio.runtime

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes access to runtime lifecycle operations.
 */
class RuntimeLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock()
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}