package com.n8n.mobile.studio.runtime

import java.io.File

/**
 * App-owned filesystem layout for the local runtime.
 *
 * The whole tree lives under the app's private files directory, so the runtimes
 * only ever see storage the app owns:
 *
 * ```
 * local-runtime/
 *   bin/          node launcher shims + PATH entries
 *   payloads/     installed runtime payloads (n8n, opencode) + versions
 *   home/         HOME for spawned processes
 *   projects/     user-visible project/filesystem root for OpenCode
 *   data/         per-component data (n8n db, opencode state)
 *   logs/         process logs, one file per component
 *   run/          pid files and service state
 *   tmp/          TMPDIR for spawned processes
 * ```
 *
 * Pure JVM: constructed from a base [File] so it can be unit tested and reused
 * by CLI tooling.
 */
class RuntimePaths(val filesDir: File, val dirName: String = "local-runtime") {

    val root: File get() = File(filesDir, dirName)
    val bin: File get() = File(root, "bin")
    val payloads: File get() = File(root, "payloads")
    val home: File get() = File(root, "home")
    val projects: File get() = File(root, "projects")
    val data: File get() = File(root, "data")
    val logs: File get() = File(root, "logs")
    val run: File get() = File(root, "run")
    val tmp: File get() = File(root, "tmp")

    fun componentData(component: EmbeddedComponent): File = File(data, component.dataDir)

    fun componentPayloadRoot(component: EmbeddedComponent): File = File(payloads, component.id)

    fun payloadDir(component: EmbeddedComponent, version: String): File =
        File(componentPayloadRoot(component), sanitize(version))

    fun currentPointer(component: EmbeddedComponent): File = File(componentPayloadRoot(component), "current")

    fun installMarker(component: EmbeddedComponent, version: String): File =
        File(payloadDir(component, version), RuntimePins.INSTALL_MARKER)

    fun logFile(component: EmbeddedComponent): File = File(logs, "${component.id}.log")

    fun pidFile(component: EmbeddedComponent): File = File(run, "${component.id}.pid")

    fun servicePidFile(): File = File(run, "service.pid")

    fun lockFile(name: String): File = File(run, "runtime-$name.lock")

    fun componentTmp(component: EmbeddedComponent): File = File(tmp, component.id)

    fun componentHome(component: EmbeddedComponent): File = File(home, component.id)

    /**
     * Resolve [relative] inside [base] refusing escapes (`..`, absolute paths).
     * Used for every archive entry so a malicious payload cannot write outside
     * the app sandbox.
     */
    fun resolveInside(base: File, relative: String): File {
        val cleaned = relative.replace('\\', '/').trimStart('/')
        require(cleaned.isNotEmpty()) { "empty path" }
        require(!cleaned.contains('\u0000')) { "nul byte in path: $relative" }
        val candidate = File(base, cleaned)
        val basePath = base.canonicalFile.path
        val target = candidate.canonicalFile
        require(target.path == basePath || target.path.startsWith(basePath + File.separator)) {
            "refusing path outside payload root: $relative"
        }
        return target
    }

    /** Create the full directory tree. Idempotent. */
    fun ensure(): Result<Unit> = runCatching {
        listOf(root, bin, payloads, home, projects, data, logs, run, tmp).forEach { dir ->
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                throw IllegalStateException("Failed to create runtime directory ${dir.absolutePath}")
            }
        }
        EmbeddedComponent.entries.forEach { component ->
            listOf(componentData(component), componentTmp(component), componentHome(component)).forEach { dir ->
                if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                    throw IllegalStateException("Failed to create ${dir.absolutePath}")
                }
            }
        }
        Unit
    }

    fun usableSpaceBytes(): Long = root.let { if (it.exists()) it.usableSpace else filesDir.usableSpace }

    private fun sanitize(version: String): String =
        version.ifBlank { "unversioned" }.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val DEFAULT_DIR = "local-runtime"

        fun forFilesDir(filesDir: File, dirName: String = DEFAULT_DIR): RuntimePaths =
            RuntimePaths(filesDir, dirName)
    }
}
