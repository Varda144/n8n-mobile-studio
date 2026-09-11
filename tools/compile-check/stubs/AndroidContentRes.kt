@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.content.res

import java.io.File
import java.io.InputStream

class AssetFileDescriptor(private val file: File) : AutoCloseable {
    val length: Long get() = file.length()
    val declaredLength: Long get() = file.length()
    fun createInputStream(): InputStream = file.inputStream()
    fun openFd(): java.io.FileDescriptor = java.io.FileInputStream(file).fd
    override fun close() {}
}

class AssetManager {
    fun open(path: String): InputStream {
        // The harness reads assets from the repository so tests can parse the real
        // packaged manifest: paths are resolved against the asset root when set.
        val resolved = assetRoot?.let { File(it, path) } ?: File(path)
        return resolved.inputStream()
    }

    fun list(path: String): Array<String> {
        val resolved = assetRoot?.let { File(it, path) } ?: File(path)
        return resolved.list() ?: emptyArray()
    }

    fun openFd(path: String): AssetFileDescriptor = AssetFileDescriptor(File(assetRoot ?: ".", path))

    companion object {
        /** Set by the check harness to `android/app/src/main/assets`. */
        var assetRoot: String? = null
    }
}

class Configuration {
    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_TYPE_PHONE = 1
        const val UI_MODE_TYPE_TABLET = 2
    }
}

class Resources {
    fun openRawResource(id: Int): InputStream = InputStream.nullInputStream()
    fun getString(id: Int): String = ""
}
