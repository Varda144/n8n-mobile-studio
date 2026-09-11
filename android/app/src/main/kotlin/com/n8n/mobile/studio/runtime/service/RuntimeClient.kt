package com.n8n.mobile.studio.runtime.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.InstallProgress
import com.n8n.mobile.studio.runtime.InstalledPayload
import com.n8n.mobile.studio.runtime.RuntimeLogLine
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The GUI's handle on the runtime service.
 *
 * Everything the Compose layer shows or triggers flows through here: status
 * snapshots, log lines, terminal commands, payload installs. Calls are forwarded
 * to the bound [LocalRuntimeService]; when the service is not bound yet the
 * client reports that state instead of pretending operations succeeded.
 */
class RuntimeClient(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _statuses = MutableStateFlow(
        EmbeddedComponent.entries.associateWith { EmbeddedComponentStatus(it) },
    )
    val statuses: StateFlow<Map<EmbeddedComponent, EmbeddedComponentStatus>> = _statuses.asStateFlow()

    private val _logs = MutableStateFlow<List<RuntimeLogLine>>(emptyList())
    val logs: StateFlow<List<RuntimeLogLine>> = _logs.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _runtimeInfo = MutableStateFlow<RuntimeInfo?>(null)
    val runtimeInfo: StateFlow<RuntimeInfo?> = _runtimeInfo.asStateFlow()

    @Volatile
    private var service: LocalRuntimeService? = null

    private var collectors = mutableListOf<Job>()
    private val binding = AtomicBoolean(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = (binder as? LocalRuntimeService.LocalBinder)?.service()
            if (local == null) {
                Logger.w(TAG, "unexpected binder from $name")
                return
            }
            service = local
            _connected.value = true
            startCollectors(local)
            refreshInfo()
            Logger.i(TAG, "bound to runtime service")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _connected.value = false
            Logger.w(TAG, "runtime service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            service = null
            _connected.value = false
            binding.set(false)
            Logger.w(TAG, "runtime service binding died; rebinding")
            ensureBound()
        }
    }

    fun ensureBound() {
        if (service != null || !binding.compareAndSet(false, true)) return
        val intent = Intent(context, LocalRuntimeService::class.java)
        val started = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!started) {
            binding.set(false)
            Logger.w(TAG, "bindService refused; runtime controls stay disabled until the service starts")
        }
    }

    fun unbind() {
        if (!binding.compareAndSet(true, false)) return
        runCatching { context.unbindService(connection) }
        collectors.forEach { it.cancel() }
        collectors.clear()
        service = null
        _connected.value = false
    }

    private fun startCollectors(local: LocalRuntimeService) {
        collectors.forEach { it.cancel() }
        collectors = mutableListOf(
            scope.launch {
                local.state.statuses.collect { map -> _statuses.value = map }
            },
            scope.launch {
                local.logs.events.collect { line ->
                    val updated = _logs.value + line
                    _logs.value = if (updated.size > MAX_LOG_LINES) updated.takeLast(MAX_LOG_LINES) else updated
                }
            },
        )
        _logs.value = local.logsSnapshot(null).takeLast(MAX_LOG_LINES)
    }

    // ---------------------------------------------------------------- controls

    fun touch(component: EmbeddedComponent) {
        service?.touch(component)
    }

    suspend fun start(component: EmbeddedComponent) = withService("start") { it.startComponent(component) }

    suspend fun stop(component: EmbeddedComponent) = withService("stop") { it.stopComponent(component) }

    suspend fun restart(component: EmbeddedComponent) = withService("restart") { it.restartComponent(component) }

    suspend fun startAll() = withService("startAll") { it.startAll("ui") }

    suspend fun stopAll() = withService("stopAll") { it.stopAll() }

    fun clearLogs(component: EmbeddedComponent?) {
        service?.clearLogs(component)
        _logs.value = emptyList()
    }

    suspend fun installFromAssets(
        component: EmbeddedComponent,
        onProgress: (InstallProgress) -> Unit = {},
    ): Result<InstalledPayload> = when (val local = service) {
        null -> Result.failure(IllegalStateException("runtime service is not connected"))
        else -> local.installFromAssets(component, onProgress)
    }

    suspend fun installFromUri(
        component: EmbeddedComponent,
        uri: Uri,
        onProgress: (InstallProgress) -> Unit = {},
    ): Result<InstalledPayload> = when (val local = service) {
        null -> Result.failure(IllegalStateException("runtime service is not connected"))
        else -> local.installFromUri(component, uri, onProgress)
    }

    suspend fun uninstall(component: EmbeddedComponent): Result<Unit> =
        runCatching { service?.uninstallPayload(component)?.getOrThrow() ?: error("runtime service is not connected") }

    suspend fun probeNode(): Result<String> = when (val local = service) {
        null -> Result.failure(IllegalStateException("runtime service is not connected"))
        else -> local.probeNode()
    }

    suspend fun runCommand(
        sessionId: String,
        input: String,
        onEvent: (TerminalOutput) -> Unit,
    ): Result<TerminalResult> = when (val local = service) {
        null -> Result.failure(IllegalStateException("runtime service is not connected"))
        else -> runCatching { local.runTerminalCommand(sessionId, input, onEvent) }
    }

    fun resetTerminal(sessionId: String) {
        service?.resetTerminalSession(sessionId)
    }

    fun refreshInfo() {
        scope.launch {
            _runtimeInfo.value = service?.runtimeInfo()
        }
    }

    private suspend fun withService(
        what: String,
        block: suspend (LocalRuntimeService) -> Unit,
    ) {
        val local = service ?: run {
            ensureBound()
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                while (service == null) kotlinx.coroutines.delay(50)
            }
            service
        }
        if (local == null) {
            Logger.w(TAG, "$what ignored: runtime service not connected")
            return
        }
        runCatching { block(local) }.onFailure { Logger.e(TAG, "$what failed", it) }
    }

    private companion object {
        const val TAG = "RuntimeClient"
        const val MAX_LOG_LINES = 2_000
        const val BIND_TIMEOUT_MS = 4_000L
    }
}
