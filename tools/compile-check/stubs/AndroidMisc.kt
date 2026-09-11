@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")

package android.net

/** Offline check stub: android.net + friends (see AndroidFramework.kt). */
class Uri private constructor(val value: String) {
    val path: String? get() = value.substringAfter("://", "").substringBefore('?').ifEmpty { null }
    val lastPathSegment: String? get() = path?.trimEnd('/')?.substringAfterLast('/')
    val scheme: String? get() = value.substringBefore("://", "").ifEmpty { null }
    val authority: String? get() = null
    val isAbsolute: Boolean get() = value.contains("://")

    override fun toString(): String = value

    companion object {
        fun parse(uriString: String): Uri = Uri(uriString)
        fun fromFile(file: java.io.File): Uri = Uri("file://${file.absolutePath}")
        fun encode(uri: Uri): String = uri.value
    }
}
