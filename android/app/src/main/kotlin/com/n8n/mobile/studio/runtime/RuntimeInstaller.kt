package com.n8n.mobile.studio.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

/**
 * Installs a runtime payload into the app-owned filesystem.
 *
 * Properties that matter on a phone:
 *  - **verified**: the archive digest is checked before anything is kept;
 *  - **atomic**: extraction happens in `payloads/.staging-*` and is renamed into
 *    place, so a killed process never leaves a half-installed runtime that the
 *    app would then try to launch;
 *  - **sandboxed**: every archive entry goes through [RuntimePaths.resolveInside],
 *    so `../` entries cannot escape the sandbox;
 *  - **pruned**: only the newest installs per component are kept, bounding the
 *    storage the studio uses.
 *
 * Pure JVM (no Android types), so the archive/digest/atomicity behaviour is
 * covered by unit tests.
 */
class RuntimeInstaller(
    private val paths: RuntimePaths,
    private val now: () -> Long = System::currentTimeMillis,
    private val reservedSpaceBytes: Long = RESERVED_SPACE_BYTES,
) {

    /**
     * Install (or re-install) a payload. Blocking IO: call from a background
     * dispatcher.
     */
    fun install(
        source: PayloadSource,
        request: InstallRequest,
        onProgress: (InstallProgress) -> Unit = {},
    ): InstalledPayload {
        paths.ensure().getOrElse { throw PayloadException(PayloadProblem.UNKNOWN, it.message ?: "runtime tree", it) }

        val available = paths.usableSpaceBytes()
        val needed = (source.sizeBytes.takeIf { it > 0 } ?: 0L) * 2 + reservedSpaceBytes
        if (available in 1 until needed) {
            throw PayloadException(
                PayloadProblem.OUT_OF_SPACE,
                "Not enough free space: ${available / MIB} MiB available, ${needed / MIB} MiB required",
            )
        }

        val staging = File(paths.payloads, ".staging-${request.component.id}-${now()}")
        staging.deleteRecursively()
        if (!staging.mkdirs()) {
            throw PayloadException(PayloadProblem.UNKNOWN, "Cannot create staging dir ${staging.absolutePath}")
        }

        try {
            val extraction = extract(source, request, staging, onProgress)

            val required = request.requiredEntries.ifEmpty { listOfNotNull(extraction.firstEntry) }
            if (required.isEmpty()) {
                throw PayloadException(PayloadProblem.ENTRY_MISSING, "Payload archive contained no files")
            }
            required.forEach { relative ->
                val file = paths.resolveInside(staging, relative)
                if (!file.isFile) {
                    throw PayloadException(
                        PayloadProblem.ENTRY_MISSING,
                        "Payload entry '$relative' is missing after extraction",
                    )
                }
            }

            val target = paths.payloadDir(request.component, request.version)
            onProgress(InstallProgress.Finalizing(target.absolutePath))
            val retired = File(paths.payloads, ".retired-${request.component.id}-${now()}")
            if (target.exists()) {
                if (!target.renameTo(retired)) target.deleteRecursively()
            }
            if (!staging.renameTo(target)) {
                throw PayloadException(PayloadProblem.UNKNOWN, "Cannot activate payload at ${target.absolutePath}")
            }
            retired.deleteRecursively()

            val installed = InstalledPayload(
                component = request.component,
                version = request.version,
                abi = request.abi,
                dir = target,
                entryRelativePath = required.first(),
                sha256 = extraction.sha256,
                sizeBytes = extraction.bytesWritten,
                installedAt = now(),
                origin = source.origin,
            )
            installed.marker.writeText(
                json.encodeToString(InstalledPayloadMarker.serializer(), installed.toMarker()),
            )
            paths.currentPointer(request.component).writeText(request.version)
            prune(request.component)
            return installed
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw when (t) {
                is PayloadException -> t
                is ZipException -> PayloadException(
                    PayloadProblem.CORRUPT_ARCHIVE,
                    "Payload archive is not a readable zip: ${t.message}",
                    t,
                )
                else -> PayloadException(PayloadProblem.UNKNOWN, t.message ?: t.javaClass.simpleName, t)
            }
        }
    }

    /** Currently active payload for a component, if a usable install exists. */
    fun installed(component: EmbeddedComponent): InstalledPayload? {
        val pointer = paths.currentPointer(component)
        if (pointer.isFile) {
            val version = pointer.readText().trim()
            payloadAt(component, version)?.takeIf { it.isUsable() }?.let { return it }
        }
        return versions(component).firstNotNullOfOrNull { version ->
            payloadAt(component, version)?.takeIf { it.isUsable() }
        }
    }

    fun payloadAt(component: EmbeddedComponent, version: String): InstalledPayload? {
        if (version.isBlank()) return null
        val dir = paths.payloadDir(component, version)
        val markerFile = File(dir, RuntimePins.INSTALL_MARKER)
        if (!markerFile.isFile) return null
        return runCatching {
            val marker = json.decodeFromString(InstalledPayloadMarker.serializer(), markerFile.readText())
            InstalledPayload.fromMarker(component, dir, marker)
        }.getOrNull()
    }

    /** Installed versions, newest first. */
    fun versions(component: EmbeddedComponent): List<String> =
        componentRootVersions(component).sortedDescending()

    fun delete(component: EmbeddedComponent, version: String): Boolean {
        val dir = paths.payloadDir(component, version)
        val removed = dir.deleteRecursively()
        val pointer = paths.currentPointer(component)
        if (removed && pointer.isFile && pointer.readText().trim() == version) pointer.delete()
        return removed
    }

    fun prune(component: EmbeddedComponent, keep: Int = KEEP_INSTALLS) {
        val current = paths.currentPointer(component).takeIf { it.isFile }?.readText()?.trim()
        versions(component)
            .filter { it != current }
            .drop(keep)
            .forEach { delete(component, it) }
        // Drop leftovers from interrupted installs.
        paths.payloads.listFiles()
            ?.filter {
                it.name.startsWith(".staging-${component.id}-") ||
                    it.name.startsWith(".retired-${component.id}-")
            }
            ?.forEach { it.deleteRecursively() }
    }

    private data class Extraction(val sha256: String, val bytesWritten: Long, val firstEntry: String?)

    private fun extract(
        source: PayloadSource,
        request: InstallRequest,
        staging: File,
        onProgress: (InstallProgress) -> Unit,
    ): Extraction {
        var bytesRead = 0L
        var bytesWritten = 0L
        var entries = 0
        var firstEntry: String? = null
        val digest = MessageDigest.getInstance("SHA-256")

        source.open().use { raw ->
            val counted = object : InputStream() {
                private val delegate = BufferedInputStream(raw, DEFAULT_BUFFER_SIZE)
                override fun read(): Int = delegate.read().also { if (it >= 0) bytesRead += 1 }
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    delegate.read(b, off, len).also { if (it > 0) { bytesRead += it; digest.update(b, off, it) } }

                override fun available(): Int = delegate.available()
                override fun close() = delegate.close()
            }

            val zip = ZipInputStream(counted, DEFAULT_BUFFER_SIZE)
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (entry.isDirectory) {
                    paths.resolveInside(staging, name).mkdirs()
                } else {
                    val target = paths.resolveInside(staging, name)
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            bytesWritten += read
                        }
                    }
                    entries++
                    if (firstEntry == null) firstEntry = name
                }
                if (entries > 0 && entries % PROGRESS_EVERY == 0) {
                    onProgress(InstallProgress.Extracting(entries, bytesWritten, bytesRead))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        onProgress(InstallProgress.Extracting(entries, bytesWritten, bytesRead))
        if (request.maxBytes > 0 && bytesRead > request.maxBytes) {
            throw PayloadException(
                PayloadProblem.CORRUPT_ARCHIVE,
                "Payload larger than the configured limit " +
                    "(${bytesRead / MIB} MiB > ${request.maxBytes / MIB} MiB)",
            )
        }
        val actual = Sha256.hex(digest.digest())
        if (request.expectedSha256.isNotBlank() && !Sha256.matches(request.expectedSha256, actual)) {
            throw PayloadException(
                PayloadProblem.INTEGRITY_MISMATCH,
                "Payload digest mismatch: expected ${request.expectedSha256.take(16)}… " +
                    "but archive is ${actual.take(16)}…",
            )
        }
        return Extraction(sha256 = actual, bytesWritten = bytesWritten, firstEntry = firstEntry)
    }

    private fun componentRootVersions(component: EmbeddedComponent): List<String> {
        val root = paths.componentPayloadRoot(component)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.filter { File(it, RuntimePins.INSTALL_MARKER).isFile }
            ?.map { it.name }
            ?: emptyList()
    }

    companion object {
        const val MIB: Long = 1024L * 1024L
        const val RESERVED_SPACE_BYTES: Long = 64L * MIB
        const val KEEP_INSTALLS = 2
        private const val PROGRESS_EVERY = 256
        private val json = Json { ignoreUnknownKeys = true }
    }
}
