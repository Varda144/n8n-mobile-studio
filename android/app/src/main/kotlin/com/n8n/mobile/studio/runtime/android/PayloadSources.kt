package com.n8n.mobile.studio.runtime.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.n8n.mobile.studio.runtime.PayloadSource
import java.io.File
import java.io.InputStream

/**
 * A runtime payload bundled in the APK at
 * `assets/runtime/<component>/payload.zip`.
 *
 * The archive is stored as an asset (not unpacked by the build), so the app
 * installs it into the sandbox on first launch, verifies its digest, and can
 * replace it later without a reinstall.
 */
class AssetPayloadSource(
    private val context: Context,
    private val assetPath: String,
    override val sizeBytes: Long = -1L,
) : PayloadSource {

    override val origin: String get() = "asset:$assetPath"

    override fun open(): InputStream = context.assets.open(assetPath)

    companion object {
        fun create(context: Context, componentId: String, archiveName: String): AssetPayloadSource? {
            val path = "runtime/$componentId/$archiveName"
            val exists = runCatching { context.assets.open(path).close() }.isSuccess
            if (!exists) return null
            // Uncompressed assets expose their length; compressed ones do not, in
            // which case the installer simply skips the space pre-flight.
            val size = runCatching { context.assets.openFd(path).length }.getOrDefault(-1L)
            return AssetPayloadSource(context, path, size)
        }
    }
}

/**
 * An archive the user picked with the system file picker, so a payload built by
 * CI can be sideloaded into the app without rebuilding the APK.
 */
class UriPayloadSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : PayloadSource {

    override val origin: String get() = uri.toString()

    override val sizeBytes: Long by lazy {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    override fun open(): InputStream =
        resolver.openInputStream(uri) ?: throw IllegalStateException("cannot open $uri")
}

/** A payload archive already on disk (used by tests and `adb push` workflows). */
class LocalFilePayloadSource(private val file: File) : PayloadSource {
    override val origin: String get() = file.absolutePath
    override val sizeBytes: Long get() = file.length()
    override fun open(): InputStream = file.inputStream()
}
