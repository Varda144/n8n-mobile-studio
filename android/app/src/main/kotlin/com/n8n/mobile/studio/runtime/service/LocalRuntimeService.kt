package com.n8n.mobile.studio.runtime.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.data.preferences.RuntimePreferences
import com.n8n.mobile.studio.security.RuntimeSecrets
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedRuntime
import com.n8n.mobile.studio.runtime.InstallProgress
import com.n8n.mobile.studio.runtime.InstalledPayload
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import com.n8n.mobile.studio.runtime.MemoryPressure
import com.n8n.mobile.studio.runtime.MemorySnapshot
import com.n8n.mobile.studio.runtime.RuntimeLogLine
import com.n8n.mobile.studio.runtime.RuntimeMemoryPolicy
import com.n8n.mobile.studio.runtime.RuntimeProcess
import com.n8n.mobile.studio.runtime.RuntimeLogBuffer
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeProcess
import com.n8n.mobile.studio.runtime.RuntimeState
import com.n8n.mobile.studio.runtime.RuntimeSupervisor
import com.n8n.mobile.studio.runtime.android.AndroidRuntimeEnv
import com.n8n.mobile.studio.runtime.android.PayloadManager
import com.n8n.mobile.studio.runtime.n8n.N8nConfig
import com.n8n.mobile.studio.runtime.n8n.N8nRuntime
import com.n8n.mobile.studio.runtime.opencode.OpenCodeConfig
import com.n8n.mobile.studio.runtime.opencode.OpenCodeRuntime
import com.n8n.mobile.studio.terminal.RuntimeControl
import com.n8n.mobile.studio.terminal.TerminalEngine
import com.n8n.mobile.studio.terminal.TerminalOutput
import com.n8n.mobile.studio.terminal.TerminalResult
import com.n8n.mobile.studio.terminal.TerminalSession
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The process manager for the local computing environment.
 *
 * It is a foreground service on purpose: n8n and OpenCode must survive the user
 * leaving the app (background executions, workflows triggered by schedule or
 * webhook), and Android only grants that to a foreground service with a visible
 * notification. The service owns:
 *
 *  - the [RuntimeSupervisor] (spawn/kill/restart, health gating, idle stop);
 *  - payload installation and verification;
 *  - the shared [RuntimeLogBuffer] and [RuntimeState] the GUI + terminal read;
 *  - the terminal command engine, whose runtime commands call the same
 *    supervisor methods as the GUI buttons.
 *
 * Widgets and activities never spawn processes directly.
 */
class LocalRuntimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    private lateinit var env: AndroidRuntimeEnv
    private lateinit var notification: RuntimeNotification
    private lateinit var preferences: RuntimePreferences
    private lateinit var secrets: RuntimeSecrets
    private lateinit var payloads: PayloadManager
    private lateinit var terminal: TerminalEngine
    private lateinit var installer: com.n8n.mobile.studio.runtime.RuntimeInstaller
    private lateinit var runtimes: LocalRuntimeManager

    lateinit var state: RuntimeState
        private set
    lateinit var logs: RuntimeLogBuffer
        private set
    lateinit var paths: RuntimePaths
        private set
    lateinit var supervisor: RuntimeSupervisor
        private set

    @Volatile
    private var config: AppConfig = AppConfig()

    @Volatile
    private var preparing = true

    private val terminalSessions = ConcurrentHashMap<String, TerminalSession>()

    @Volatile
    private var boundClients = 0

    inner class LocalBinder : Binder() {
        fun service(): LocalRuntimeService = this@LocalRuntimeService
    }

    // ------------------------------------------------------------ service hooks

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "runtime service created")

        env = AndroidRuntimeEnv(this)
        paths = env.paths
        installer = env.installer()
        state = RuntimeState()
        logs = RuntimeLogBuffer()
        payloads = PayloadManager(this, env, installer)
        preferences = RuntimePreferences(this)
        secrets = RuntimeSecrets()
        notification = RuntimeNotification(this)
        notification.createChannel()

        startForegroundCompat(notification.build(state.snapshot(), preparing = true))

        runtimes = LocalRuntimeManager(
            paths = paths,
            installer = installer,
            node = env.node(),
            manifest = { payloads.manifest() },
            n8nConfig = {
                N8nConfig(
                    port = config.n8nPort,
                    memoryMb = heapFor(EmbeddedComponent.N8N),
                    encryptionKey = secrets.n8nEncryptionKey(),
                )
            },
            openCodeConfig = {
                OpenCodeConfig(
                    port = config.openCodePort,
                    memoryMb = heapFor(EmbeddedComponent.OPENCODE),
                    providerKeys = secrets.providerKeys(),
                )
            },
        )

        supervisor = RuntimeSupervisor(
            scope = scope,
            state = state,
            logs = logs,
            runtimeFor = ::runtimeFor,
            config = { config },
        ).apply {
            pidFiles { paths.pidFile(it) }
        }

        terminal = TerminalEngine(
            paths = paths,
            node = env.node(),
            control = ServiceRuntimeControl(),
        )

        binder.silently { paths.ensure() }
        supervisor.start()

        scope.launch { observeConfiguration() }
        scope.launch { observeState() }
        scope.launch { bootstrap() }
        scope.launch { monitorMemory() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Logger.i(TAG, "onStartCommand action=$action startId=$startId")
        when (action) {
            null, AppConstants.ACTION_START -> scope.launch { startAll("service start") }
            AppConstants.ACTION_STOP -> scope.launch {
                supervisor.stopAll("notification")
                delay(STOP_GRACE_MS)
                stopForegroundCompat()
                stopSelf()
            }
            AppConstants.ACTION_RESTART -> scope.launch {
                EmbeddedComponent.entries.forEach { supervisor.restartComponent(it) }
            }
            AppConstants.ACTION_START_N8N -> scope.launch { supervisor.startComponent(EmbeddedComponent.N8N) }
            AppConstants.ACTION_STOP_N8N -> scope.launch { supervisor.stopComponent(EmbeddedComponent.N8N) }
            AppConstants.ACTION_RESTART_N8N -> scope.launch { supervisor.restartComponent(EmbeddedComponent.N8N) }
            AppConstants.ACTION_START_OPENCODE -> scope.launch { supervisor.startComponent(EmbeddedComponent.OPENCODE) }
            AppConstants.ACTION_STOP_OPENCODE -> scope.launch { supervisor.stopComponent(EmbeddedComponent.OPENCODE) }
            AppConstants.ACTION_RESTART_OPENCODE ->
                scope.launch { supervisor.restartComponent(EmbeddedComponent.OPENCODE) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        boundClients++
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        boundClients = (boundClients - 1).coerceAtLeast(0)
        return true
    }

    override fun onDestroy() {
        Logger.i(TAG, "runtime service destroyed")
        supervisor.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        scope.launch { supervisor.onMemoryPressure(env.memorySnapshot().copy(lowMemory = true)) }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        env.lastTrimLevel = level
        if (level >= TRIM_RUNNING_LOW) {
            scope.launch { supervisor.onMemoryPressure(env.memorySnapshot()) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    /**
     * Swiping the app away must not stop local workflows, so the service stays
     * alive; the notification remains the user's control surface.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.i(TAG, "task removed; keeping local runtime alive")
        super.onTaskRemoved(rootIntent)
    }

    // -------------------------------------------------------------- client API

    fun configSnapshot(): AppConfig = config

    fun runtimeInfo(): RuntimeInfo {
        val manifest = payloads.manifest()
        val node = env.node()
        val memory = env.memorySnapshot()
        return RuntimeInfo(
            appVersion = env.appVersionName(),
            appVersionCode = env.appVersionCode(),
            packageName = env.packageName(),
            abis = env.abis,
            activeAbi = node.abi(),
            nodePackaged = manifest.node?.packaged ?: false,
            nodeVersion = manifest.node?.version.orEmpty(),
            nodeLauncherPresent = node.launcher() != null,
            nodeLibraryPresent = node.library() != null,
            nativeLibraryDir = env.nativeLibraryDir.absolutePath,
            runtimeRoot = paths.root.absolutePath,
            projectsDir = paths.projects.absolutePath,
            configDir = paths.componentData(EmbeddedComponent.N8N).absolutePath,
            memoryClassMb = memory.memoryClassMb,
            totalMemoryMb = memory.totalMb,
            availableMemoryMb = memory.availMb,
            payloadVersions = EmbeddedComponent.entries.associateWith { component ->
                payloads.installedVersion(component)?.version
            },
            packagedComponents = payloads.manifest().runtimes.mapValues { it.value.packaged },
            providerCredentials = secrets.configuredProviders(),
            n8nEncryptionKeyPresent = secrets.hasN8nEncryptionKey(),
        )
    }

    fun snapshotStatuses(): List<EmbeddedComponentStatus> = state.snapshot()

    fun touch(component: EmbeddedComponent) = supervisor.touch(component)

    suspend fun startComponent(component: EmbeddedComponent) = supervisor.startComponent(component)

    suspend fun stopComponent(component: EmbeddedComponent) = supervisor.stopComponent(component)

    suspend fun restartComponent(component: EmbeddedComponent) = supervisor.restartComponent(component)

    suspend fun startAll(reason: String) {
        supervisor.startAll(env.memorySnapshot().also { logMemory(it) })
        Logger.i(TAG, "startAll requested ($reason)")
    }

    suspend fun stopAll() = supervisor.stopAll()

    fun logsSnapshot(component: EmbeddedComponent?): List<RuntimeLogLine> =
        logs.snapshot(component)

    fun clearLogs(component: EmbeddedComponent?) = logs.clear(component)

    suspend fun installFromAssets(
        component: EmbeddedComponent,
        onProgress: (InstallProgress) -> Unit,
    ): Result<InstalledPayload> = payloads.installFromAssets(component, onProgress).onSuccess {
        payloads.invalidate()
        supervisor.refreshPayload(component, it.version)
        Logger.i(TAG, "installed ${component.id} payload ${it.version} (${it.sizeBytes} bytes)")
    }.onFailure {
        supervisor.refreshPayload(component, null, it.message)
    }

    suspend fun installFromUri(
        component: EmbeddedComponent,
        uri: Uri,
        onProgress: (InstallProgress) -> Unit,
    ): Result<InstalledPayload> = payloads.installFromUri(component, uri, onProgress).onSuccess {
        supervisor.refreshPayload(component, it.version)
    }.onFailure {
        supervisor.refreshPayload(component, null, it.message)
    }

    fun uninstallPayload(component: EmbeddedComponent): Result<Unit> =
        payloads.uninstall(component).onSuccess { supervisor.refreshPayload(component, null) }

    suspend fun probeNode(): Result<String> = env.node().probeVersion()

    suspend fun runTerminalCommand(
        sessionId: String,
        input: String,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val session = terminalSessions.getOrPut(sessionId) { TerminalSession(root = paths.root) }
        supervisor.touch(EmbeddedComponent.N8N)
        val result = terminal.execute(input, session, onEvent)
        logs.append(
            RuntimeLogLine(
                component = null,
                text = "terminal: $input",
                channel = RuntimeLogLine.Channel.SYSTEM,
            ),
        )
        return result
    }

    fun resetTerminalSession(sessionId: String) {
        terminalSessions.remove(sessionId)
    }

    fun terminalPrompt(sessionId: String): String =
        terminalSessions[sessionId]?.prompt() ?: "~ $"

    // ------------------------------------------------------------------ private

    private fun runtimeFor(component: EmbeddedComponent): EmbeddedRuntime = runtimes.runtime(component)

    private fun heapFor(component: EmbeddedComponent): Int {
        val requested = payloads.manifest().spec(component).memoryMb
        return RuntimeMemoryPolicy.nodeHeapMb(env.memorySnapshot(), requested)
    }

    private suspend fun bootstrap() {
        config = preferences.config.first()
        paths.ensure().onFailure { Logger.e(TAG, "runtime tree failed: ${it.message}") }

        // A crashed service can leave processes whose stdout pipe is gone; they
        // are reclaimed instead of being adopted half-blind.
        supervisor.killOrphans(runtimes::payloadMarker)

        EmbeddedComponent.entries.forEach { component ->
            val status = payloads.status(component)
            supervisor.refreshPayload(component, status.installed?.version, status.message)
        }

        val startable = EmbeddedComponent.entries.filter { config.enabled(it) }
        prepareAndStart(startable)
        preparing = false

        val probe = env.node().probeVersion()
        probe.onSuccess { Logger.i(TAG, "embedded Node v$it is runnable on this device") }
        probe.onFailure { Logger.w(TAG, "embedded Node is not runnable yet: ${it.message}") }
    }

    private suspend fun prepareAndStart(components: List<EmbeddedComponent>) {
        components.forEach { component ->
            val status = payloads.status(component)
            if (!status.ready && status.assetAvailable) {
                Logger.i(TAG, "installing bundled ${component.id} payload")
                installFromAssets(component) { }
            }
        }
        val wantsN8n = config.autoStartN8n && config.n8nEnabled
        val wantsOpenCode = config.autoStartOpenCode && config.openCodeEnabled
        if (wantsN8n) supervisor.startComponent(EmbeddedComponent.N8N, "auto-start")
        if (wantsOpenCode) {
            val plan = RuntimeMemoryPolicy.plan(env.memorySnapshot(), config, state.activeComponents())
            if (plan.allowConcurrentRuntimes || !wantsN8n) {
                supervisor.startComponent(EmbeddedComponent.OPENCODE, "auto-start")
            } else {
                Logger.i(TAG, "OpenCode auto-start deferred: ${plan.pressure}")
            }
        }
    }

    private suspend fun observeConfiguration() {
        preferences.config.collect { updated ->
            val previous = config
            config = updated
            if (previous.n8nPort != updated.n8nPort &&
                state.status(EmbeddedComponent.N8N).state.isActive
            ) {
                Logger.i(TAG, "n8n port changed; restarting runtime")
                supervisor.restartComponent(EmbeddedComponent.N8N)
            }
            if (previous.openCodePort != updated.openCodePort &&
                state.status(EmbeddedComponent.OPENCODE).state.isActive
            ) {
                Logger.i(TAG, "OpenCode port changed; restarting runtime")
                supervisor.restartComponent(EmbeddedComponent.OPENCODE)
            }
        }
    }

    private suspend fun observeState() {
        state.statuses.collect {
            notification.update(state.snapshot(), preparing = preparing)
        }
    }

    private suspend fun monitorMemory() {
        while (scope.isActive) {
            delay(MEMORY_POLL_MS)
            val snapshot = env.memorySnapshot()
            val plan = RuntimeMemoryPolicy.plan(snapshot, config, state.activeComponents())
            if (plan.pressure != MemoryPressure.NORMAL) {
                supervisor.onMemoryPressure(snapshot)
            }
        }
    }

    private fun logMemory(snapshot: MemorySnapshot) {
        Logger.i(
            TAG,
            "memory: ${snapshot.availMb} MiB free of ${snapshot.totalMb} MiB " +
                "(class ${snapshot.memoryClassMb} MiB, lowMemory=${snapshot.lowMemory})",
        )
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(AppConstants.NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /** Terminal → supervisor bridge: same methods the GUI calls. */
    private inner class ServiceRuntimeControl : RuntimeControl {
        override fun status(component: EmbeddedComponent): EmbeddedComponentStatus = state.status(component)

        override suspend fun start(component: EmbeddedComponent): String {
            supervisor.startComponent(component, "terminal")
            return "${component.label}: start requested"
        }

        override suspend fun stop(component: EmbeddedComponent): String {
            supervisor.stopComponent(component, "terminal")
            return "${component.label}: stop requested"
        }

        override suspend fun restart(component: EmbeddedComponent): String {
            supervisor.restartComponent(component)
            return "${component.label}: restart requested"
        }
    }

    private companion object {
        const val TAG = "LocalRuntimeService"
        const val STOP_GRACE_MS = 750L
        const val MEMORY_POLL_MS = 30_000L
        const val TRIM_RUNNING_LOW = 10
    }
}

/**
 * Snapshot of the device + payload facts shown in Settings; makes "is this
 * runtime really embedded?" answerable on device without adb.
 */
data class RuntimeInfo(
    val appVersion: String,
    val appVersionCode: Long,
    val packageName: String,
    val abis: List<String>,
    val activeAbi: String?,
    val nodePackaged: Boolean,
    val nodeVersion: String,
    val nodeLauncherPresent: Boolean,
    val nodeLibraryPresent: Boolean,
    val nativeLibraryDir: String,
    val runtimeRoot: String,
    val projectsDir: String,
    val configDir: String,
    val memoryClassMb: Int,
    val totalMemoryMb: Long,
    val availableMemoryMb: Long,
    val payloadVersions: Map<EmbeddedComponent, String?>,
    val packagedComponents: Map<String, Boolean>,
    val providerCredentials: List<String>,
    val n8nEncryptionKeyPresent: Boolean,
)

/** Small helper so optional work in the binder path never crashes the service. */
private fun Binder.silently(block: () -> Unit) {
    runCatching(block)
}
