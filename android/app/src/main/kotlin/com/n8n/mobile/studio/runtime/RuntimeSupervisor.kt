package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Injectable process creation, so the supervisor is testable without spawning. */
fun interface ProcessSpawner {
    fun spawn(spec: LaunchSpec, onEvent: (ProcessEvent) -> Unit): Result<SpawnedProcess>

    companion object {
        val default = ProcessSpawner { spec, onEvent -> RuntimeProcess.launch(spec, onEvent) }
    }
}

/** Injectable health probing. */
fun interface HealthProber {
    suspend fun probe(url: String, paths: List<String>): RuntimeStatus

    companion object {
        val default = HealthProber { url, paths -> RuntimeHealth().probe(url, paths) }
    }
}

/**
 * Owns the lifecycle of both embedded runtimes.
 *
 * Responsibilities:
 *  - turn user intent (`start`/`stop`/`restart`) and device conditions (memory
 *    pressure, idle time) into [RuntimePolicy] events;
 *  - spawn and kill the actual OS processes through [ProcessSpawner];
 *  - probe health on loopback and promote a process to
 *    [EmbeddedProcessState.RUNNING] only when it really answers;
 *  - publish one consistent [RuntimeState] that the GUI, terminal and
 *    notification all read from.
 *
 * All policy decisions are synchronous and serialised by [mutex]; the process
 * work happens outside the lock. The supervisor never touches Android APIs, so
 * its behaviour is unit tested with fake spawners and fake health probes.
 */
class RuntimeSupervisor(
    private val scope: CoroutineScope,
    private val state: RuntimeState,
    private val logs: RuntimeLogBuffer,
    private val runtimeFor: (EmbeddedComponent) -> EmbeddedRuntime,
    private val config: () -> AppConfig,
    private val spawner: ProcessSpawner = ProcessSpawner.default,
    private val health: HealthProber = HealthProber.default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickIntervalMs: Long = 1_000,
    private val healthIntervalMs: Long = 4_000,
) {

    private val runtimes = ConcurrentHashMap<EmbeddedComponent, ComponentRuntime>()
    private val processes = ConcurrentHashMap<EmbeddedComponent, SpawnedProcess>()
    private val prepared = ConcurrentHashMap<EmbeddedComponent, PreparedRuntime>()
    private val mutex = Mutex()

    @Volatile
    private var loop: Job? = null

    init {
        EmbeddedComponent.entries.forEach { component ->
            val runtime = runtimeFor(component)
            val spec = runtime.spec()
            runtimes[component] = RuntimePolicy.initial(component, payloadReady = false, version = spec.version)
                .copy(message = "Runtime payload not installed")
            publish(runtimes.getValue(component))
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /** Start the periodic tick/health loop. Idempotent. */
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                tick()
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    /** Resolve the installed payload; call once per service start and after installs. */
    suspend fun refreshPayload(component: EmbeddedComponent, installedVersion: String?, problem: String? = null) {
        val now = clock()
        if (installedVersion != null) {
            dispatch(component, RuntimeEvent.PayloadResolved(now, installedVersion))
        } else {
            dispatch(
                component,
                RuntimeEvent.PayloadMissing(
                    now,
                    problem ?: "${component.label} runtime payload is not installed on this device",
                ),
            )
        }
    }

    suspend fun startComponent(component: EmbeddedComponent, reason: String = "user") {
        log(component, "start requested ($reason)")
        dispatch(component, RuntimeEvent.StartRequested(clock()))
    }

    suspend fun stopComponent(component: EmbeddedComponent, reason: String = "user") {
        log(component, "stop requested ($reason)")
        dispatch(component, RuntimeEvent.StopRequested(clock(), reason))
    }

    suspend fun restartComponent(component: EmbeddedComponent) {
        log(component, "restart requested")
        dispatch(component, RuntimeEvent.StopRequested(clock(), "restart"))
        dispatch(component, RuntimeEvent.StartRequested(clock()))
    }

    /**
     * Start n8n, and OpenCode too when the device can afford a second Node
     * process; otherwise OpenCode is deferred and the reason is surfaced.
     */
    suspend fun startAll(snapshot: MemorySnapshot?) {
        val app = config()
        val plan = snapshot?.let { RuntimeMemoryPolicy.plan(it, app, state.activeComponents()) }
        startComponent(EmbeddedComponent.N8N, "app start")

        val openCodeDeferred = plan?.deferComponents?.contains(EmbeddedComponent.OPENCODE) == true ||
            plan?.allowConcurrentRuntimes == false
        if (!app.openCodeEnabled) return
        if (openCodeDeferred) {
            log(EmbeddedComponent.OPENCODE, "deferred: device memory does not allow two runtimes at once")
            state.update(EmbeddedComponent.OPENCODE) {
                it.copy(message = "Waiting for free memory before starting OpenCode")
            }
        } else {
            startComponent(EmbeddedComponent.OPENCODE, "app start")
        }
    }

    suspend fun stopAll(reason: String = "user") {
        EmbeddedComponent.entries.forEach { stopComponent(it, reason) }
    }

    /** Mark activity so idle auto-stop does not fire during real use. */
    fun touch(component: EmbeddedComponent) {
        val current = runtimes[component] ?: return
        runtimes[component] = current.copy(lastActivityAt = clock())
        scope.launch { dispatch(component, RuntimeEvent.Activity(clock())) }
    }

    /**
     * Kill runtime processes left over from a previous service instance.
     *
     * Their stdout pipe died with the old service, so they can never be adopted:
     * the honest move is to reclaim the pid and start a fresh, observable process.
     */
    fun killOrphans(markerFor: (EmbeddedComponent) -> String): List<Long> {
        val killed = mutableListOf<Long>()
        EmbeddedComponent.entries.forEach { component ->
            val pidFile = preparedPidFiles[component] ?: return@forEach
            val pid = RuntimeProcess.readPid(pidFile)
            if (pid != null && RuntimeProcess.isAlive(pid) && RuntimeProcess.isOurs(pid, markerFor(component))) {
                log(component, "reclaiming orphaned runtime process pid=$pid from a previous service run")
                ProcessKiller.kill(pid)
                killed += pid
            }
            pidFile.delete()
        }
        return killed
    }

    private val preparedPidFiles = ConcurrentHashMap<EmbeddedComponent, java.io.File>()

    /** Supplies the pid-file locations; the Android layer sets this from [RuntimePaths]. */
    fun pidFiles(provider: (EmbeddedComponent) -> java.io.File) {
        EmbeddedComponent.entries.forEach { component ->
            preparedPidFiles[component] = provider(component)
        }
    }

    // ------------------------------------------------------------------ events

    private suspend fun tick() {
        val now = clock()
        EmbeddedComponent.entries.forEach { component ->
            val current = runtimes[component]
            val process = processes[component]
            val policy = PolicyConfig(idleStopMs = config().idleStopMillis(component))
            val deadlinePassed = current != null &&
                current.state == EmbeddedProcessState.STARTING &&
                current.pid != null &&
                current.spawnDeadlineAt > 0 &&
                now >= current.spawnDeadlineAt
            // A runtime that is alive but slow (n8n migrating its database on a
            // phone) must not be killed for being slow. Only the supervisor knows
            // whether the process is still there, so it asks the policy to extend
            // the deadline instead of letting it time out.
            if (deadlinePassed && process?.isAlive() == true &&
                (current?.spawnExtensions ?: 0) < policy.maxSpawnExtensions
            ) {
                dispatch(component, RuntimeEvent.SpawnStalled(now))
            } else {
                dispatch(component, RuntimeEvent.Tick(now))
            }
        }
        probeHealth()
    }

    private suspend fun probeHealth() {
        EmbeddedComponent.entries.forEach { component ->
            val current = runtimes[component] ?: return@forEach
            if (current.pid == null) return@forEach
            if (!current.state.isActive) return@forEach
            val spec = runtimeFor(component).spec()
            val url = healthUrl(component) ?: return@forEach
            if (spec.healthPath.isBlank()) {
                dispatch(component, RuntimeEvent.HealthOk(clock()))
                return@forEach
            }
            val result = runCatching { health.probe(url, listOf(spec.healthPath, "/")) }.getOrNull()
            if (result == null || !result.reachable) {
                dispatch(component, RuntimeEvent.HealthFailed(clock(), result?.detail ?: "probe failed"))
            } else {
                dispatch(component, RuntimeEvent.HealthOk(clock()))
            }
        }
    }

    private suspend fun dispatch(component: EmbeddedComponent, event: RuntimeEvent) {
        // Failure handling is itself an event ("the launch was refused", "the payload
        // could not be prepared"). The mutex is not reentrant, so those events are
        // collected while the lock is held and replayed once it is released — a
        // supervisor that wedged on its own error path would leave the UI stuck in
        // STARTING forever, which is exactly the state a user cannot recover from.
        val followUps = mutex.withLock { applyLocked(component, event) }
        followUps.forEach { followUp -> dispatch(component, followUp) }
    }

    /** The locked half of [dispatch]: decide, publish, act, and report follow-ups. */
    private suspend fun applyLocked(
        component: EmbeddedComponent,
        event: RuntimeEvent,
    ): List<RuntimeEvent> {
        val current = runtimes[component] ?: return emptyList()
        val app = config()
        val policy = PolicyConfig(idleStopMs = app.idleStopMillis(component))
        val result = RuntimePolicy.decide(current, event, policy)

        var next = result.next
        val now = clock()
        if (result.actions.contains(RuntimeAction.Spawn) && next.spawnDeadlineAt <= 0) {
            next = next.copy(spawnDeadlineAt = now + policy.spawnTimeoutMs)
        }
        if (result.actions.contains(RuntimeAction.TerminateGracefully) && next.stopDeadlineAt <= 0) {
            next = next.copy(stopDeadlineAt = now + policy.stopGraceMs)
        }
        if (result.actions.isEmpty() && next == current) return emptyList()

        // A runtime that dies usually explains itself on its own stderr. The
        // device cannot attach a debugger and the raw log is a tab away, so the
        // last thing the process said is folded into the status the UI shows —
        // a screenshot of the LOCAL tab then carries the real reason.
        val published = withFailureReason(component, next)
        runtimes[component] = published
        publish(published)
        logTransition(current, published)

        val followUps = mutableListOf<RuntimeEvent>()
        result.actions.forEach { action ->
            when (action) {
                RuntimeAction.Spawn -> spawn(component)?.let(followUps::add)
                RuntimeAction.TerminateGracefully -> terminate(component, force = false)
                RuntimeAction.TerminateForcefully -> terminate(component, force = true)
            }
        }
        return followUps
    }

    /**
     * Attaches the tail of the process output to a failure status.
     *
     * Only for states the user must act on ([EmbeddedProcessState.FAILED]) and
     * only when the policy did not already supply a detail, so a healthy or
     * merely restarting runtime keeps the concise message the policy produced.
     */
    private fun withFailureReason(
        component: EmbeddedComponent,
        next: ComponentRuntime,
    ): ComponentRuntime {
        if (next.state != EmbeddedProcessState.FAILED || next.healthDetail != null) return next
        val recent = logs.snapshot(component).takeLast(8).filter { it.text.isNotBlank() }
        val output = recent.filter { it.channel != RuntimeLogLine.Channel.SYSTEM }.takeLast(3)
        if (output.isNotEmpty()) {
            val detail = output.joinToString(" | ") { it.text.trim().take(200) }
            return next.copy(healthDetail = "last output: $detail")
        }
        // No output at all: the only explanation is the supervisor's own note
        // that the process could not be started in the first place.
        val launchProblem = recent
            .filter { it.text.startsWith("launch failed") || it.text.startsWith("prepare failed") }
            .takeLast(1)
            .joinToString("") { it.text.trim().take(200) }
        return if (launchProblem.isBlank()) next else next.copy(healthDetail = launchProblem)
    }

    private fun logTransition(previous: ComponentRuntime, next: ComponentRuntime) {
        if (previous.state != next.state || previous.message != next.message) {
            log(next.component, "${next.state.name.lowercase()}: ${next.message}")
        }
    }

    // ----------------------------------------------------------------- process

    /**
     * Spawn one runtime. Returns the event that must be dispatched for a failure —
     * never dispatching it here, because this runs with the supervisor lock held.
     */
    private suspend fun spawn(component: EmbeddedComponent): RuntimeEvent? {
        val runtime = runtimeFor(component)
        val preparedRuntime = prepared[component]
            ?: runtime.prepare().getOrElse { error ->
                log(component, "prepare failed: ${error.message}")
                return RuntimeEvent.PayloadMissing(clock(), error.message ?: "prepare failed")
            }
        prepared[component] = preparedRuntime

        val result = spawner.spawn(preparedRuntime.launch) { event -> onProcessEvent(component, event) }
        return result.fold(
            onSuccess = { managed ->
                processes[component] = managed
                preparedPidFiles[component] = preparedRuntime.launch.pidFile
                null
            },
            onFailure = { error ->
                log(component, "launch failed: ${error.message}")
                RuntimeEvent.ProcessExited(clock(), exitCode = null, expected = false)
            },
        )
    }

    private fun onProcessEvent(component: EmbeddedComponent, event: ProcessEvent) {
        when (event) {
            is ProcessEvent.Started -> scope.launch {
                log(component, "spawned pid=${event.pid ?: "unknown"} :: ${event.commandLine}")
                // Persist the pid: after a service restart it is the only way to
                // reclaim a runtime the previous process left behind.
                event.pid?.let { pid -> writePidFile(component, pid) }
                dispatch(component, RuntimeEvent.ProcessStarted(clock(), event.pid))
            }
            is ProcessEvent.Output -> logs.append(
                RuntimeLogLine(component = component, text = event.line, at = event.at),
            )
            is ProcessEvent.Exited -> scope.launch {
                processes.remove(component)
                clearPidFile(component)
                log(component, "process exited (code=${event.exitCode ?: "signal"})")
                dispatch(component, RuntimeEvent.ProcessExited(event.at, event.exitCode))
            }
        }
    }

    private fun writePidFile(component: EmbeddedComponent, pid: Long) {
        val file = preparedPidFiles[component] ?: return
        runCatching {
            file.parentFile?.mkdirs()
            // The engine publishes its own pid through the preload into this very
            // file. Never overwrite a pid the payload recorded for a process that
            // is still alive — ours is a fallback, not an authority.
            val recorded = RuntimeProcess.readPid(file)
            if (recorded == null || !RuntimeProcess.isAlive(recorded)) {
                file.writeText("$pid\n")
            }
        }
    }

    private fun clearPidFile(component: EmbeddedComponent) {
        val file = preparedPidFiles[component] ?: return
        runCatching { file.delete() }
    }

    private fun terminate(component: EmbeddedComponent, force: Boolean) {
        val managed = processes[component] ?: return
        log(component, if (force) "killing runtime process" else "terminating runtime process")
        if (force) managed.kill() else managed.terminate()
        if (force) {
            managed.pid?.takeIf { it > 0 }?.let(ProcessKiller::kill)
            processes.remove(component)
            scope.launch { dispatch(component, RuntimeEvent.ProcessExited(clock(), managed.exitCode())) }
        }
    }

    /** Called by the Android layer when the OS reports memory pressure. */
    suspend fun onMemoryPressure(snapshot: MemorySnapshot) {
        val plan = RuntimeMemoryPolicy.plan(snapshot, config(), state.activeComponents())
        logs.append(
            RuntimeLogLine(
                component = null,
                text = "memory: pressure=${plan.pressure} avail=${snapshot.availMb}MiB " +
                    "concurrent=${plan.allowConcurrentRuntimes}",
                channel = RuntimeLogLine.Channel.SYSTEM,
            ),
        )
        plan.stopComponents.forEach { component ->
            dispatch(component, RuntimeEvent.LowMemory(clock(), snapshot.availMb, critical = true))
        }
    }

    // ------------------------------------------------------------------ status

    fun snapshot(): List<EmbeddedComponentStatus> = state.snapshot()

    fun runtimeForTesting(component: EmbeddedComponent): ComponentRuntime? = runtimes[component]

    private fun publish(runtime: ComponentRuntime) {
        val spec = runtimeFor(runtime.component).spec()
        state.set(
            EmbeddedComponentStatus(
                component = runtime.component,
                state = runtime.state,
                pid = runtime.pid,
                endpoint = healthUrl(runtime.component)?.removeSuffix(spec.healthPath.ifBlank { "" }),
                version = runtime.payloadVersion.ifBlank { null },
                message = runtime.message,
                healthDetail = runtime.healthDetail,
                restartCount = runtime.restartCount,
                startedAt = runtime.startedAt.takeIf { it > 0 },
                payloadReady = runtime.payloadReady,
            ),
        )
    }

    private fun healthUrl(component: EmbeddedComponent): String? {
        val port = config().portFor(component)
        return if (port in 1..65535) "http://127.0.0.1:$port" else null
    }

    private fun log(component: EmbeddedComponent, text: String) {
        logs.append(
            RuntimeLogLine(
                component = component,
                text = text,
                channel = RuntimeLogLine.Channel.SYSTEM,
                at = clock(),
            ),
        )
    }
}

/**
 * Signal delivery for pids the app no longer has a [Process] handle for.
 *
 * The Android application installs a real implementation based on
 * `android.os.Process.sendSignal`; the default is a no-op so unit tests never
 * touch the host's processes.
 */
object ProcessKiller {
    @Volatile
    var killer: (Long) -> Unit = { }

    fun kill(pid: Long) {
        if (pid > 0) killer(pid)
    }
}
