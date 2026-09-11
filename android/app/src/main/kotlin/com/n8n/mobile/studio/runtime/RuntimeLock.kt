package com.n8n.mobile.studio.runtime

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises runtime lifecycle work.
 *
 * Two levels are needed on Android:
 *  - a coroutine [Mutex] because several UI coroutines may ask the supervisor to
 *    start/stop a runtime at the same moment;
 *  - an advisory file lock because the runtime tree is on disk and a previous
 *    process instance may still be finishing a stop when a new one starts.
 */
class RuntimeLock(lockFile: File? = null) {

    private val mutex = Mutex()
    private val lockFile: File? = lockFile

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /** Run [block] while holding the cross-process file lock for [name]. */
    fun <T> withFileLock(name: String, block: () -> T): T {
        val file = lockFile ?: return block()
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { channel ->
            var lock: FileLock? = null
            val deadline = System.currentTimeMillis() + LOCK_WAIT_MS
            while (lock == null && System.currentTimeMillis() < deadline) {
                lock = try {
                    channel.channel.lock()
                } catch (_: OverlappingFileLockException) {
                    Thread.sleep(LOCK_POLL_MS)
                    null
                }
            }
            try {
                return block()
            } finally {
                runCatching { lock?.release() }
            }
        }
    }

    private companion object {
        const val LOCK_WAIT_MS = 5_000L
        const val LOCK_POLL_MS = 50L
    }
}
