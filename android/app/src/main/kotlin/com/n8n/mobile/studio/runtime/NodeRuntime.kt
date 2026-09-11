package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppConstants
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Node.js runtime embedded by this app.
 *
 * The payload build cross-compiles the pinned Node version for Android with
 * `--dest-os=android --shared`, producing two shared objects that ship in the
 * APK's `jniLibs` (the only location Android allows an app to execute from):
 *
 *  - `libnode.so`     — Node itself, built as a shared library;
 *  - `libnoderun.so`  — a ~10 line launcher that calls `node::Start` and writes
 *                       its own pid into `$N8N_STUDIO_PIDFILE`, so the app owns a
 *                       real OS pid for kill/reclaim instead of guessing.
 *
 * Both n8n and OpenCode are plain JavaScript payloads executed by this Node, not
 * separate toolchains. Nothing here executes code from a writable directory.
 */
class NodeRuntime(
    private val nativeLibraryDir: File,
    private val paths: RuntimePaths,
    private val deviceAbis: List<String> = RuntimePins.SUPPORTED_ABIS,
) {

    fun launcher(): File? = File(nativeLibraryDir, RuntimePins.NODE_LAUNCHER_LIB).takeIf { it.isFile }

    fun library(): File? = File(nativeLibraryDir, RuntimePins.NODE_CORE_LIB).takeIf { it.isFile }

    /** The ABI this device will actually run, when the payload covers it. */
    fun abi(): String? = deviceAbis.firstOrNull { RuntimePins.SUPPORTED_ABIS.contains(it) }

    fun isAvailable(): Boolean = launcher() != null && library() != null

    fun missingReason(): String? = when {
        launcher() == null && library() == null ->
            "Node runtime missing: neither ${RuntimePins.NODE_LAUNCHER_LIB} nor ${RuntimePins.NODE_CORE_LIB} " +
                "is packaged for this ABI (${deviceAbis.joinToString(", ")})"
        library() == null -> "Node runtime incomplete: ${RuntimePins.NODE_CORE_LIB} is missing"
        launcher() == null -> "Node runtime incomplete: ${RuntimePins.NODE_LAUNCHER_LIB} is missing"
        else -> null
    }

    /**
     * Environment shared by every spawned runtime process.
     *
     * `PATH` points at the app-owned `bin` directory first, so tools the studio
     * ships win over anything a hostile app may have put on the system path.
     */
    fun nodeEnv(
        component: EmbeddedComponent,
        heapMb: Int,
        workingDir: File,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> = buildMap {
        put("HOME", paths.componentHome(component).absolutePath)
        put("TMPDIR", paths.componentTmp(component).absolutePath)
        put(
            "PATH",
            listOf(
                paths.bin.absolutePath,
                nativeLibraryDir.absolutePath,
                "/system/bin",
                "/vendor/bin",
            ).joinToString(":"),
        )
        put("PWD", workingDir.absolutePath)
        put("LD_LIBRARY_PATH", nativeLibraryDir.absolutePath)
        put("NODE_ENV", "production")
        put("NODE_OPTIONS", "--max-old-space-size=$heapMb")
        put("NODE_COMPILE_CACHE", File(paths.componentTmp(component), "node-compile-cache").absolutePath)
        // Phones have few useful cores for a Node thread pool; keeping it small
        // avoids the runtime holding onto dozens of idle threads.
        put("UV_THREADPOOL_SIZE", "2")
        put("NO_COLOR", "1")
        put("TERM", "dumb")
        putAll(extra)
    }

    /** Build the launch description for a component entry script. */
    fun launchSpec(
        component: EmbeddedComponent,
        entry: File,
        args: List<String>,
        workingDir: File,
        heapMb: Int,
        extraEnv: Map<String, String>,
        logFile: File = paths.logFile(component),
        pidFile: File = paths.pidFile(component),
    ): LaunchSpec {
        val launcher = launcher()
            ?: throw PayloadException(
                PayloadProblem.MISSING_FROM_ASSETS,
                missingReason() ?: "Node launcher is not packaged",
            )
        val env = nodeEnv(
            component = component,
            heapMb = heapMb,
            workingDir = workingDir,
            extra = extraEnv + mapOf(AppConstants.ENV_PIDFILE to pidFile.absolutePath),
        )
        return LaunchSpec(
            component = component,
            executable = launcher,
            args = listOf(entry.absolutePath) + args,
            workingDir = workingDir,
            env = env,
            logFile = logFile,
            pidFile = pidFile,
            label = "${component.label} (node)",
        )
    }

    /**
     * Run `libnoderun.so --version` to prove the packaged Node actually starts on
     * this device. Preflight evidence instead of a claim.
     */
    suspend fun probeVersion(timeoutMs: Long = PROBE_TIMEOUT_MS): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val launcher = launcher()
                ?: throw PayloadException(
                    PayloadProblem.MISSING_FROM_ASSETS,
                    missingReason() ?: "Node launcher is not packaged",
                )
            paths.ensure().getOrThrow()
            val builder = ProcessBuilder(listOf(launcher.absolutePath, "--version"))
                .directory(paths.tmp)
                .redirectErrorStream(true)
            builder.environment().putAll(
                nodeEnv(
                    component = EmbeddedComponent.N8N,
                    heapMb = RuntimePins.MIN_NODE_HEAP_MB,
                    workingDir = paths.tmp,
                ),
            )
            val process = builder.start()
            val output = process.inputStream.bufferedReader().use { it.readLine() }.orEmpty().trim()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("node --version timed out after ${timeoutMs}ms")
            }
            if (process.exitValue() != 0 || output.isBlank()) {
                throw IllegalStateException("node --version failed (exit=${process.exitValue()}): $output")
            }
            output.removePrefix("v")
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 15_000L
    }
}
