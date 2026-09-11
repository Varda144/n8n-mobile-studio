package com.n8n.mobile.studio.runtime

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.Serializable

/** SHA-256 helpers shared by the installer, the verifier and the build scripts. */
object Sha256 {

    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun of(file: File): String = file.inputStream().use { of(it) }

    /** Digest a stream, returning hex and the number of bytes consumed. */
    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    /** Constant-length, case-insensitive comparison tolerating `sha256:` prefixes. */
    fun matches(expected: String, actual: String): Boolean {
        val left = expected.removePrefix("sha256:").trim()
        return left.equals(actual.trim(), ignoreCase = true)
    }

    private const val HEX = "0123456789abcdef"
}

/** Where a payload archive comes from (APK asset, imported file, test fixture). */
interface PayloadSource {
    /** Human readable origin, shown in the UI and written into install.json. */
    val origin: String

    /** Known size when available; -1 when unknown. */
    val sizeBytes: Long

    fun open(): InputStream
}

class FilePayloadSource(private val file: File) : PayloadSource {
    override val origin: String get() = file.absolutePath
    override val sizeBytes: Long get() = file.length()
    override fun open(): InputStream = file.inputStream()
}

class ByteArrayPayloadSource(
    private val bytes: ByteArray,
    override val origin: String = "memory",
) : PayloadSource {
    override val sizeBytes: Long get() = bytes.size.toLong()
    override fun open(): InputStream = bytes.inputStream()
}

/** Classification of a payload problem so the UI can suggest a real next step. */
enum class PayloadProblem {
    NOT_PACKAGED,
    MISSING_FROM_ASSETS,
    INTEGRITY_MISMATCH,
    CORRUPT_ARCHIVE,
    OUT_OF_SPACE,
    ENTRY_MISSING,
    ABI_UNAVAILABLE,
    UNKNOWN,
}

class PayloadException(
    val problem: PayloadProblem,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class InstallRequest(
    val component: EmbeddedComponent,
    val version: String,
    /** Expected sha256 of the archive; empty disables the check (dev imports only). */
    val expectedSha256: String = "",
    val abi: String = "",
    /** Entry scripts that must exist in the archive, first one is the launch entry. */
    val requiredEntries: List<String> = emptyList(),
    /** Reject archives above this size (0 = unlimited). Guards against bad imports. */
    val maxBytes: Long = 0,
)

sealed interface InstallProgress {
    data class Extracting(val entries: Int, val bytesWritten: Long, val bytesRead: Long) : InstallProgress
    data class Verifying(val bytesRead: Long, val expectedSha256: String) : InstallProgress
    data class Finalizing(val payloadDir: String) : InstallProgress
}

/** Result of a successful payload install. */
data class InstalledPayload(
    val component: EmbeddedComponent,
    val version: String,
    val abi: String,
    val dir: File,
    val entryRelativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val installedAt: Long,
    val origin: String,
) {
    val entry: File get() = File(dir, entryRelativePath)
    val marker: File get() = File(dir, RuntimePins.INSTALL_MARKER)

    fun isUsable(): Boolean = dir.isDirectory && entry.isFile

    fun toMarker(): InstalledPayloadMarker = InstalledPayloadMarker(
        component = component.id,
        version = version,
        abi = abi,
        entry = entryRelativePath,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        installedAt = installedAt,
        origin = origin,
    )

    companion object {
        fun fromMarker(component: EmbeddedComponent, dir: File, marker: InstalledPayloadMarker): InstalledPayload =
            InstalledPayload(
                component = component,
                version = marker.version,
                abi = marker.abi,
                dir = dir,
                entryRelativePath = marker.entry,
                sha256 = marker.sha256,
                sizeBytes = marker.sizeBytes,
                installedAt = marker.installedAt,
                origin = marker.origin,
            )
    }
}

/** Contents of `install.json`, written next to every installed payload. */
@Serializable
data class InstalledPayloadMarker(
    val component: String = "",
    val version: String = "",
    val abi: String = "",
    val entry: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0,
    val installedAt: Long = 0,
    val origin: String = "",
    val schemaVersion: Int = RuntimePins.SCHEMA_VERSION,
)
