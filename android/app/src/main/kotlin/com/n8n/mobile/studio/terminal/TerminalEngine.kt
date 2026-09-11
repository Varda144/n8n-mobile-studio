package com.n8n.mobile.studio.terminal

import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.RuntimeProcess
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Runs [TerminalCommand]s by delegating process launch to [RuntimeProcess] and
 * tailing the captured log file until the process exits.
 */
class TerminalEngine {

    suspend fun run(
        command: TerminalCommand,
        environment: TerminalEnvironment,
        onEvent: (TerminalOutput) -> Unit,
    ): Result<TerminalSession> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val buffer = TerminalOutputBuffer()
        try {
            val cwd = command.cwd ?: File(environment.values["PWD"] ?: ".")
            val commandLine = listOf(command.executable) + command.args
            val logFile = File(resolveTmpDir(environment.values), "$id.log")

            emitLine(buffer, onEvent, "$ ${command.displayName}", TerminalOutput.Channel.SYSTEM)
            Logger.d(TAG, "Launching ${command.displayName}")

            when (val launch = RuntimeProcess.launch(commandLine, cwd, environment.values, logFile)) {
                is Result.Failure -> {
                    val error = launch.exceptionOrNull()
                        ?: IllegalStateException("failed to launch ${command.displayName}")
                    emitLine(buffer, onEvent, "error: ${error.message}", TerminalOutput.Channel.SYSTEM)
                    onEvent(TerminalOutput.Exited(1))
                    Result.failure<TerminalSession>(error)
                }

                is Result.Success -> {
                    val pid = launch.getOrThrow()
                    Logger.d(TAG, "Session $id launched with pid=$pid")
                    pollUntilExited(pid, logFile, buffer, onEvent)
                    onEvent(TerminalOutput.Exited(0))
                    val session = TerminalSession(id = id, command = command, output = buffer)
                    session.finish()
                    Result.success(session)
                }
            }
        } catch (t: Throwable) {
            Logger.e(TAG, "Session $id failed", t)
            emitLine(
                buffer,
                onEvent,
                "error: ${t.message ?: t.javaClass.simpleName}",
                TerminalOutput.Channel.SYSTEM,
            )
            onEvent(TerminalOutput.Exited(1))
            Result.failure(t)
        }
    }

    private suspend fun pollUntilExited(
        pid: Long,
        logFile: File,
        buffer: TerminalOutputBuffer,
        onEvent: (TerminalOutput) -> Unit,
    ) {
        var lastRead = 0L
        val pending = StringBuilder()
        while (RuntimeProcess.isAlive(pid)) {
            lastRead = drainLog(logFile, lastRead, pending) { line ->
                emitLine(buffer, onEvent, line, TerminalOutput.Channel.STDOUT)
            }
            delay(POLL_INTERVAL_MS)
        }
        lastRead = drainLog(logFile, lastRead, pending) { line ->
            emitLine(buffer, onEvent, line, TerminalOutput.Channel.STDOUT)
        }
        if (pending.isNotEmpty()) {
            emitLine(buffer, onEvent, pending.toString(), TerminalOutput.Channel.STDOUT)
            pending.setLength(0)
        }
    }

    /** Append any new bytes written since [lastRead], splitting on newlines. */
    private fun drainLog(
        logFile: File,
        lastRead: Long,
        pending: StringBuilder,
        onLine: (String) -> Unit,
    ): Long {
        val length = logFile.length()
        if (length <= lastRead) return lastRead
        var read = lastRead
        logFile.inputStream().use { input ->
            if (input.skip(lastRead) == lastRead) {
                pending.append(String(input.readBytes(), Charsets.UTF_8))
                read = length
            }
        }
        var start = 0
        var idx = pending.indexOf("\n")
        while (idx >= 0) {
            onLine(pending.substring(start, idx).trimEnd('\r'))
            start = idx + 1
            idx = pending.indexOf("\n", start)
        }
        if (start > 0) {
            val leftover = pending.substring(start)
            pending.setLength(0)
            pending.append(leftover)
        }
        return read
    }

    private fun resolveTmpDir(env: Map<String, String>): File {
        env["TMPDIR"]?.let { File(it) }?.let { dir ->
            if (dir.exists() || dir.mkdirs()) return dir
        }
        return File(System.getProperty("java.io.tmpdir") ?: "/tmp")
    }

    private fun emitLine(
        buffer: TerminalOutputBuffer,
        onEvent: (TerminalOutput) -> Unit,
        text: String,
        channel: TerminalOutput.Channel,
    ) {
        val line = TerminalOutput.Line(text, channel)
        buffer.add(line)
        onEvent(line)
    }

    private companion object {
        const val TAG = "TerminalEngine"
        const val POLL_INTERVAL_MS = 200L
    }
}