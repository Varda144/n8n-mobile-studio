package com.n8n.mobile.studio

import android.app.Application
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.ProcessKiller
import com.n8n.mobile.studio.runtime.RuntimeProcess
import com.n8n.mobile.studio.runtime.android.AndroidRuntimeEnv
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.security.SecureStorage
import com.n8n.mobile.studio.security.SecureStorageHolder

/**
 * Process-wide wiring for the local runtime.
 *
 * Three things must happen exactly once per app process:
 *  - install the Android signal terminator (`android.os.Process.sendSignal`),
 *    because plain `java.lang.Process` has no pid on Android;
 *  - make Keystore-backed storage available to the runtime configuration code;
 *  - create the single [RuntimeClient] the UI binds to the runtime service.
 */
class StudioApplication : Application() {

    val runtimeClient: RuntimeClient by lazy { RuntimeClient(this) }

    override fun onCreate() {
        super.onCreate()
        SecureStorageHolder.initialise(SecureStorage(this))

        RuntimeProcess.terminator = object : com.n8n.mobile.studio.runtime.ProcessTerminator {
            override fun terminate(process: Process, pid: Long?) {
                if (pid != null && pid > 0) {
                    runCatching { android.os.Process.sendSignal(pid.toInt(), SIGNAL_TERM) }
                }
                runCatching { process.destroy() }
            }

            override fun kill(process: Process, pid: Long?) {
                if (pid != null && pid > 0) {
                    runCatching { android.os.Process.sendSignal(pid.toInt(), SIGNAL_KILL) }
                }
                runCatching { process.destroyForcibly() }
            }
        }

        ProcessKiller.killer = { pid ->
            runCatching { android.os.Process.sendSignal(pid.toInt(), SIGNAL_KILL) }
            Unit
        }

        val env = AndroidRuntimeEnv(this)
        Logger.i(TAG, "app start: package=${env.packageName()} version=${env.appVersionName()} abis=${env.abis}")
        Logger.i(
            TAG,
            "native libs: launcher=${env.node().launcher() != null} lib=${env.node().library() != null} " +
                "dir=${env.nativeLibraryDir.absolutePath}",
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.d(TAG, "onTrimMemory($level)")
    }

    private companion object {
        const val TAG = "StudioApplication"
        const val SIGNAL_TERM = 15
        const val SIGNAL_KILL = 9
    }
}
