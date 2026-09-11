package com.n8n.mobile.studio.runtime.android

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.MemorySnapshot
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimeInstaller
import com.n8n.mobile.studio.runtime.RuntimeManifest
import com.n8n.mobile.studio.runtime.RuntimePaths
import java.io.File

/**
 * Everything the pure runtime core needs to know about the Android device.
 *
 * This is the single place where Android APIs meet the runtime code, which keeps
 * the rest of the runtime package testable on a plain JVM.
 */
class AndroidRuntimeEnv(private val context: Context) {

    val paths: RuntimePaths = RuntimePaths.forFilesDir(context.filesDir, AppConstants.RUNTIME_DIR)

    val nativeLibraryDir: File = File(context.applicationInfo.nativeLibraryDir)

    val abis: List<String> = Build.SUPPORTED_ABIS.toList()

    @Volatile
    var lastTrimLevel: Int = 0

    fun node(): NodeRuntime = NodeRuntime(nativeLibraryDir, paths, abis)

    fun installer(): RuntimeInstaller = RuntimeInstaller(paths)

    /** The manifest packaged inside this APK build. */
    fun manifest(): RuntimeManifest = try {
        val text = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
        RuntimeManifest.parse(text).getOrElse { error ->
            Logger.e(TAG, "packaged runtime manifest is invalid: ${error.message}")
            RuntimeManifest()
        }
    } catch (_: Throwable) {
        Logger.w(TAG, "no packaged runtime manifest at $MANIFEST_ASSET")
        RuntimeManifest()
    }

    fun memorySnapshot(): MemorySnapshot {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val mib = 1024L * 1024L
        return MemorySnapshot(
            totalMb = info.totalMem / mib,
            availMb = info.availMem / mib,
            lowMemory = info.lowMemory,
            memoryClassMb = manager.memoryClass,
            trimLevel = lastTrimLevel,
        )
    }

    fun appVersionName(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    }.getOrDefault("unknown")

    fun appVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    fun packageName(): String = context.packageName

    /** Whether the APK declares any runtime payload at all. */
    fun packagedComponents(): List<EmbeddedComponent> = try {
        val names = context.assets.list(ASSET_RUNTIME_DIR)?.toList() ?: emptyList()
        EmbeddedComponent.entries.filter { component ->
            names.contains(component.id) &&
                (context.assets.list("$ASSET_RUNTIME_DIR/${component.id}")?.isNotEmpty() == true)
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val TAG = "AndroidRuntimeEnv"
        const val MANIFEST_ASSET = "runtime/manifest.json"
        private const val ASSET_RUNTIME_DIR = "runtime"
    }
}
