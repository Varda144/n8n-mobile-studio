package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppConstants
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Node.js engine embedded by this app.
 *
 * `scripts/build-node-android.sh` cross-compiles the pinned Node release for
 * Android using upstream Node's own Android support (`./android-configure` from
 * the Node source tree) and packages the resulting executable as
 * `jniLibs/<abi>/libnode.so`, with the NDK C++ runtime next to it.
 *
 * Packaging it as a native library is not cosmetic: Android only executes app
 * files that come from the APK's `lib/<abi>/` directory, which is exactly what
 * `ApplicationInfo.nativeLibraryDir` points at. Nothing here executes code from a
 * writable directory, and n8n/OpenCode are plain JavaScript payloads run by this
 * Node — no second toolchain, no remote server.
 */
class NodeRuntime(
    private val nativeLibraryDir: File,
    private val paths: RuntimePaths,
    private val deviceAbis: List<String> = RuntimePins.SUPPORTED_ABIS,
) {

    /** The Node executable, when the payload build packaged it for this ABI. */
    fun binary(): File? = File(nativeLibraryDir, RuntimePins.NODE_CORE_LIB).takeIf { it.isFile }

    /** Shared libraries the Node binary needs at load time. */
    fun missingLibraries(): List<String> =
        RuntimePins.NODE_EXTRA_LIBS.filterNot { File(nativeLibraryDir, it).isFile }

    /** The ABI this device will actually run, when the payload covers it. */
    fun abi(): String? = deviceAbis.firstOrNull { RuntimePins.SUPPORTED_ABIS.contains(it) }

    fun isAvailable(): Boolean = binary() != null && missingLibraries().isEmpty()

    /** Human-readable reason the engine cannot run here, or null when it can. */
    fun missingReason(): String? = when {
        binary() == null && missingLibraries().isNotEmpty() ->
            "Node engine missing: ${RuntimePins.NODE_CORE_LIB} and " +
                "${missingLibraries().joinToString(", ")} are not packaged for this ABI " +
                "(${deviceAbis.joinToString(", ")})"
        binary() == null ->
            "Node engine missing: ${RuntimePins.NODE_CORE_LIB} is not packaged for this ABI " +
                "(${deviceAbis.joinToString(", ")})"
        missingLibraries().isNotEmpty() ->
            "Node engine incomplete: ${missingLibraries().joinToString(", ")} missing"
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
        // The engine lives in nativeLibraryDir together with the NDK C++ runtime;
        // both the dynamic linker (via the binary's $ORIGIN rpath) and these
        // variables resolve it there.
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
        val engine = binary()
            ?: throw PayloadException(
                PayloadProblem.MISSING_FROM_ASSETS,
                missingReason() ?: "Node engine is not packaged",
            )
        val env = nodeEnv(
            component = component,
            heapMb = heapMb,
            workingDir = workingDir,
            extra = extraEnv + mapOf(AppConstants.ENV_PIDFILE to pidFile.absolutePath),
        )
        return LaunchSpec(
            component = component,
            executable = engine,
            args = listOf(entry.absolutePath) + args,
            workingDir = workingDir,
            env = env,
            logFile = logFile,
            pidFile = pidFile,
            label = "${component.label} (node)",
        )
    }

    /**
     * Run `libnode.so --version` to prove the packaged engine actually starts on
     * this device. Preflight evidence instead of a claim.
     */
    suspend fun probeVersion(timeoutMs: Long = PROBE_TIMEOUT_MS): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val engine = binary()
                ?: throw PayloadException(
                    PayloadProblem.MISSING_FROM_ASSETS,
                    missingReason() ?: "Node engine is not packaged",
                )
            paths.ensure().getOrThrow()
            val builder = ProcessBuilder(listOf(engine.absolutePath, "--version"))
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
