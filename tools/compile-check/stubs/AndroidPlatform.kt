@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch")

package android.os

/** Offline check stub: android.os subset (see AndroidFramework.kt). */
open class Binder : IBinder

interface IBinder

object Build {
    object VERSION {
        @JvmField
        var SDK_INT: Int = 36
    }

    object VERSION_CODES {
        const val BASE = 1
        const val N = 24
        const val N_MR1 = 25
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
    }

    @JvmField
    val SUPPORTED_ABIS: Array<String> = arrayOf("arm64-v8a", "armeabi-v7a")

    @JvmField
    val SUPPORTED_64_BIT_ABIS: Array<String> = arrayOf("arm64-v8a")

    @JvmField
    val MANUFACTURER: String = "check"

    @JvmField
    val MODEL: String = "check"

    @JvmField
    val DEVICE: String = "check"

    @JvmField
    val HARDWARE: String = "check"

    @JvmField
    val FINGERPRINT: String = "check"
}

object Process {
    fun myPid(): Int = 0
    fun myUid(): Int = 0
    fun myTid(): Int = 0
    const val THREAD_PRIORITY_BACKGROUND = 10
    const val THREAD_PRIORITY_DEFAULT = 0
    fun setThreadPriority(priority: Int): Boolean = true
}

object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
    fun sleep(ms: Long) {
        Thread.sleep(ms)
    }
}

object Debug {
    fun isLoggable(tag: String?, level: Int): Boolean = false
}

class Handler(private val looper: Looper = Looper.getMainLooper()) {
    fun post(action: Runnable): Boolean = true
    fun removeCallbacks(action: Runnable) {}
    fun postDelayed(action: Runnable, delayMs: Long): Boolean = true
}

class Looper {
    companion object {
        fun getMainLooper(): Looper = Looper()
        fun myLooper(): Looper? = Looper()
    }
}

class StatFs(private val path: String) {
    fun getAvailableBytes(): Long = java.io.File(path).usableSpace
    fun getTotalBytes(): Long = java.io.File(path).totalSpace
    fun getBlockSizeLong(): Long = 4096
}

class ParcelFileDescriptor {
    companion object {
        fun open(file: java.io.File, mode: Int): ParcelFileDescriptor = ParcelFileDescriptor()
        const val MODE_READ_ONLY = 268435456
    }

    fun close() {}
    fun detachFd(): Int = -1
}

class Environment {
    companion object {
        fun getExternalStorageDirectory(): java.io.File = java.io.File("/sdcard")
        val DIRECTORY_DOWNLOADS: String = "Download"
        fun getExternalStoragePublicDirectory(type: String): java.io.File = java.io.File("/sdcard/$type")
        fun isExternalStorageManager(): Boolean = false
    }
}

class MemoryFile {
    fun getInputStream(): java.io.InputStream = java.io.InputStream.nullInputStream()
}

class Bundle

class Message

class ResultReceiver
