package com.n8n.mobile.studio.runtime

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A fully described runtime process launch.
 *
 * Built by the component runtimes ([com.n8n.mobile.studio.runtime.n8n.N8nProcess],
 * [com.n8n.mobile.studio.runtime.opencode.OpenCodeProcess]) and executed by
 * [RuntimeProcess]. Nothing here shells out: the executable is always an
 * absolute path to a binary inside the app's own sandbox.
 */
data class LaunchSpec(
    val component: EmbeddedComponent,
    val executable: File,
    val args: List<String> = emptyList(),
    val workingDir: File,
    val env: Map<String, String> = emptyMap(),
    val logFile: File,
    val pidFile: File,
    val label: String = executable.name,
) {
    fun commandLine(): List<String> = listOf(executable.absolutePath) + args

    override fun toString(): String =
        (listOf(label) + args).joinToString(" ")
}

sealed interface ProcessEvent {
    data class Started(val pid: Long?, val commandLine: String) : ProcessEvent
    data class Output(val line: String, val at: Long) : ProcessEvent
    data class Exited(val exitCode: Int?, val at: Long) : ProcessEvent
}

/**
 * Signal delivery differs per platform: Android exposes
 * `android.os.Process.sendSignal`, plain JVM only `Process.destroy*`. The
 * Android application installs its own terminator at startup, tests keep the
 * default.
 */
interface ProcessTerminator {
    fun terminate(process: Process, pid: Long?)
    fun kill(process: Process, pid: Long?)
}

object JvmProcessTerminator : ProcessTerminator {
    override fun terminate(process: Process, pid: Long?) {
        runCatching { process.destroy() }
    }

    override fun kill(process: Process, pid: Long?) {
        runCatching { process.destroyForcibly() }
    }
}

/**
 * The subset of a spawned process the supervisor uses.
 *
 * Extracted as an interface so the lifecycle engine can be unit tested with a
 * fake process — the state machine's behaviour is the part that must never
 * regress, and it must be checkable without spawning a real Node build.
 */
interface SpawnedProcess {
    val pid: Long?

    fun isAlive(): Boolean

    fun terminate()

    fun kill()

    fun waitForExit(timeoutMs: Long): Boolean

    fun exitCode(): Int?
}

/** A launched runtime process plus its merged output stream. */
class ManagedProcess internal constructor(
    val spec: LaunchSpec,
    private val handle: Process,
    override val pid: Long?,
    private val terminator: ProcessTerminator,
    private val pump: Thread?,
) : SpawnedProcess {
    override fun isAlive(): Boolean = runCatching { handle.isAlive }.getOrDefault(false)

    /** Ask the process to exit; the supervisor force-kills after a grace window. */
    override fun terminate() = terminator.terminate(handle, pid)

    override fun kill() = terminator.kill(handle, pid)

    override fun waitForExit(timeoutMs: Long): Boolean =
        runCatching { handle.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)

    override fun exitCode(): Int? = runCatching { if (handle.isAlive) null else handle.exitValue() }.getOrNull()

    internal fun joinPump(timeoutMs: Long) {
        pump?.let { runCatching { it.join(timeoutMs) } }
    }
}

/**
 * Process launching, log pumping, pid tracking and orphan detection.
 *
 * Merged stdout/stderr are appended to the component log file (rotated at
 * [MAX_LOG_BYTES]) and forwarded to `onEvent`, so the terminal view and the
 * log view observe exactly the same bytes.
 */
object RuntimeProcess {

    /** Replaced by the Android application with a signal-based terminator. */
    @Volatile
    var terminator: ProcessTerminator = JvmProcessTerminator

    private const val MAX_LOG_BYTES = 4L * 1024 * 1024
    private const val PID_WAIT_MS = 3_000L
    private const val PID_POLL_MS = 25L

    fun launch(spec: LaunchSpec, onEvent: (ProcessEvent) -> Unit): Result<ManagedProcess> = runCatching {
        spec.logFile.parentFile?.mkdirs()
        spec.pidFile.parentFile?.mkdirs()
        spec.pidFile.delete()
        spec.workingDir.mkdirs()
        rotateIfNeeded(spec.logFile)

        val builder = ProcessBuilder(spec.commandLine())
            .directory(spec.workingDir)
            .redirectErrorStream(true)
        builder.environment().putAll(spec.env)

        val stdout = ProcessBuilder.Redirect.appendTo(spec.logFile)
        builder.redirectOutput(stdout)

        val handle = builder.start()
        // The spawned process *is* Node (the engine is packaged as an executable
        // under nativeLibraryDir), so the OS pid is read straight from the handle.
        // A pid file is still honoured first for payloads that ship a wrapper.
        val pid = readPid(spec.pidFile)?.takeIf { isAlive(it) } ?: pidOf(handle)
        onEvent(ProcessEvent.Started(pid, spec.toString()))

        val output = FileOutputStream(spec.logFile, true)
        val closed = AtomicBoolean(false)
        val pump = thread(name = "runtime-log-${spec.component.id}", isDaemon = true) {
            try {
                handle.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        runCatching { output.write((line + "\n").toByteArray()); output.flush() }
                        onEvent(ProcessEvent.Output(line, System.currentTimeMillis()))
                    }
                }
            } catch (_: Throwable) {
                // Stream closed because the process exited; nothing to report.
            } finally {
                if (closed.compareAndSet(false, true)) runCatching { output.close() }
            }
        }

        val managed = ManagedProcess(spec, handle, pid, terminator, pump)
        // Exit watcher: guarantees an Exited event even when the pump ends first.
        thread(name = "runtime-wait-${spec.component.id}", isDaemon = true) {
            runCatching { handle.waitFor() }
            pump.join(2_000)
            onEvent(ProcessEvent.Exited(managed.exitCode(), System.currentTimeMillis()))
        }
        managed
    }

    fun readPid(pidFile: File): Long? =
        runCatching { pidFile.readText().trim().toLongOrNull() }.getOrNull()?.takeIf { it > 0 }

    /** Whether a pid still exists according to `/proc`. */
    fun isAlive(pid: Long, procRoot: File = File("/proc")): Boolean = File(procRoot, pid.toString()).isDirectory

    /** Command line of a live pid, or null. */
    fun cmdline(pid: Long, procRoot: File = File("/proc")): String? =
        runCatching { File(File(procRoot, pid.toString()), "cmdline").readText().replace('\u0000', ' ').trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Whether a live pid belongs to this app's runtime payload. Guards against
     * killing an unrelated process that happens to reuse a stale pid.
     */
    fun isOurs(pid: Long, marker: String, procRoot: File = File("/proc")): Boolean {
        val cmdline = cmdline(pid, procRoot) ?: return false
        return cmdline.contains(marker)
    }

    /**
     * OS pid of a launched process.
     *
     * Reflection keeps this working across Android releases: `Process.pid()` is
     * not present on every implementation, and the field name differs between
     * ART and OpenJDK.
     */
    fun pidOf(handle: Process): Long? {
        runCatching { (handle.javaClass.getMethod("pid").invoke(handle) as? Int)?.toLong() }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }
        return fallbackPid(handle)
    }

    /** Wait until a wrapper publishes its pid into [pidFile]. */
    fun waitForPid(pidFile: File, timeoutMs: Long = PID_WAIT_MS, pollMs: Long = PID_POLL_MS): Long? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            readPid(pidFile)?.let { if (isAlive(it)) return it }
            Thread.sleep(pollMs)
        }
        return readPid(pidFile)
    }

    private fun fallbackPid(handle: Process): Long? =
        runCatching {
            val field = handle.javaClass.getDeclaredField("pid").apply { isAccessible = true }
            (field.get(handle) as? Int)?.toLong()
        }.getOrNull()

    private fun rotateIfNeeded(logFile: File) {
        if (logFile.isFile && logFile.length() > MAX_LOG_BYTES) {
            val rotated = File(logFile.parentFile, logFile.name + ".1")
            rotated.delete()
            logFile.renameTo(rotated)
        }
    }
}
