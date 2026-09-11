@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.content.pm

import java.io.File

open class PackageManager {
    class NameNotFoundException : Exception()
    class PackageInfo {
        var versionName: String? = null
        var longVersionCode: Long = 0
        var versionCode: Int = 0
        var packageName: String = ""
    }

    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo()
    fun getLaunchIntentForPackage(packageName: String): Any? = null
    fun resolveActivity(intent: Any?, flags: Int): Any? = null
    fun getInstalledPackages(flags: Int): List<PackageInfo> = emptyList()

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
        const val GET_META_DATA = 128
    }
}

class ApplicationInfo {
    var nativeLibraryDir: String? = null
    var sourceDir: String? = null
    var dataDir: String? = null
    var flags: Int = 0
    var uid: Int = 0
    companion object {
        const val FLAG_EXTRACT_NATIVE_LIBS = 268435456
    }
}

class ServiceInfo {
    companion object {
        const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1
        const val FOREGROUND_SERVICE_TYPE_MANIFEST = -1
        const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1073741824
    }
}

class PackageInstaller
