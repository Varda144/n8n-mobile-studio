package com.n8n.mobile.studio.runtime

import java.io.File
import java.io.RandomAccessFile

/** One line of runtime output, shared by the log view and the terminal view. */
data class RuntimeLogLine(
    val component: EmbeddedComponent?,
    val text: String,
    val channel: Channel = Channel.STDOUT,
    val at: Long = System.currentTimeMillis(),
) {
    enum class Channel { STDOUT, STDERR, SYSTEM }
}

/**
 * Bounded, thread-safe history of runtime output.
 *
 * Phones cannot hold an unbounded console history in RAM: the buffer keeps the
 * newest [capacity] lines and drops the oldest, mirroring what the user can
 * actually see.
 */
class RuntimeLogBuffer(val capacity: Int = 4_000) {

    private val guard = Any()
    private val lines = ArrayDeque<RuntimeLogLine>()

    /**
     * Live stream of every appended line. The GUI log view and the terminal both
     * subscribe to this, so they can never show different output for the same
     * process.
     */
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<RuntimeLogLine>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: kotlinx.coroutines.flow.SharedFlow<RuntimeLogLine> = _events

    fun append(line: RuntimeLogLine) {
        synchronized(guard) {
            lines.addLast(line)
            while (lines.size > capacity) lines.removeFirst()
        }
        _events.tryEmit(line)
    }

    fun appendAll(newLines: Iterable<RuntimeLogLine>) = newLines.forEach(::append)

    fun snapshot(component: EmbeddedComponent? = null): List<RuntimeLogLine> = synchronized(guard) {
        if (component == null) lines.toList() else lines.filter { it.component == component }
    }

    fun size(): Int = synchronized(guard) { lines.size }

    fun clear(component: EmbeddedComponent? = null) {
        synchronized(guard) {
            if (component == null) {
                lines.clear()
            } else {
                val kept = lines.filterNot { it.component == component }
                lines.clear()
                lines.addAll(kept)
            }
        }
    }
}

/**
 * Reads the tail of a log file without loading it all into memory.
 */
class LogTailer(private val maxBytes: Int = 96 * 1024) {

    /** Last [maxBytes] of [file] as lines, oldest first. */
    fun tail(file: File, maxLines: Int = 400): List<String> {
        if (!file.isFile || file.length() == 0L) return emptyList()
        val start = (file.length() - maxBytes).coerceAtLeast(0)
        val text = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray((file.length() - start).toInt().coerceAtLeast(0))
                raf.readFully(buffer)
                String(buffer, Charsets.UTF_8)
            }
        }.getOrDefault("")
        return text.split('\n')
            .let { if (start > 0) it.drop(1) else it }
            .filter { it.isNotEmpty() }
            .takeLast(maxLines)
    }
}
