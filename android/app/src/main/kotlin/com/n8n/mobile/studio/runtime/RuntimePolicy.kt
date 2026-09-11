package com.n8n.mobile.studio.runtime

/** What the user (or the app's own lifecycle rules) wants a component to be. */
enum class DesiredState { STOPPED, RUNNING }

/**
 * Complete lifecycle state of one component. The supervisor keeps exactly one of
 * these per runtime and derives every UI/log message from it.
 */
data class ComponentRuntime(
    val component: EmbeddedComponent,
    val desired: DesiredState = DesiredState.STOPPED,
    val state: EmbeddedProcessState = EmbeddedProcessState.NOT_INSTALLED,
    val pid: Long? = null,
    val payloadReady: Boolean = false,
    val payloadVersion: String = "",
    val healthFailures: Int = 0,
    val consecutiveFailures: Int = 0,
    val restartCount: Int = 0,
    val nextRetryAt: Long = 0,
    /** Stamped by the supervisor; when exceeded a stopping process is killed. */
    val stopDeadlineAt: Long = 0,
    /** Stamped by the supervisor; when exceeded a starting process is killed. */
    val spawnDeadlineAt: Long = 0,
    val startedAt: Long = 0,
    val lastHealthyAt: Long = 0,
    val lastActivityAt: Long = 0,
    val lastExitCode: Int? = null,
    val gaveUp: Boolean = false,
    val message: String = "Runtime payload not installed",
    val healthDetail: String? = null,
) {
    val isRunning: Boolean get() = pid != null && state.isActive
}

sealed interface RuntimeEvent {
    data class StartRequested(val at: Long) : RuntimeEvent
    data class StopRequested(val at: Long, val reason: String = "user") : RuntimeEvent
    data class PayloadResolved(val at: Long, val version: String) : RuntimeEvent
    data class PayloadMissing(val at: Long, val message: String) : RuntimeEvent
    data class ProcessStarted(val at: Long, val pid: Long?) : RuntimeEvent
    data class HealthOk(val at: Long) : RuntimeEvent
    data class HealthFailed(val at: Long, val detail: String) : RuntimeEvent
    data class ProcessExited(val at: Long, val exitCode: Int?, val expected: Boolean = false) : RuntimeEvent
    /** Something touched the runtime (GUI opened, API call, terminal command). */
    data class Activity(val at: Long) : RuntimeEvent
    data class Tick(val at: Long) : RuntimeEvent
    data class LowMemory(val at: Long, val availMb: Long, val critical: Boolean) : RuntimeEvent
}

sealed interface RuntimeAction {
    data object Spawn : RuntimeAction
    data object TerminateGracefully : RuntimeAction
    data object TerminateForcefully : RuntimeAction
}

data class PolicyConfig(
    /** Restarts allowed before the supervisor gives up and tells the user. */
    val maxRestarts: Int = 5,
    val baseBackoffMs: Long = 1_000,
    val maxBackoffMs: Long = 60_000,
    /** Consecutive failed health probes tolerated before DEGRADED. */
    val healthFailureTolerance: Int = 3,
    /** Time a process gets to become healthy before it is killed and retried. */
    val spawnTimeoutMs: Long = 120_000,
    /** Time a stopping process gets before SIGKILL. */
    val stopGraceMs: Long = 8_000,
    /** Stop after this much inactivity once RUNNING (0 disables idle stop). */
    val idleStopMs: Long = 0,
)

data class PolicyResult(val next: ComponentRuntime, val actions: List<RuntimeAction>)

/**
 * The lifecycle state machine for one runtime.
 *
 * Deliberately pure: it takes the current state plus one event and returns the
 * next state and a small list of primitive actions (spawn / terminate /
 * reconcile). All scheduling, process handling and HTTP probing lives in
 * [RuntimeSupervisor]; this file is what the unit tests pin down, which is how
 * "n8n and OpenCode behave identically and never crash-loop" is guaranteed.
 */
object RuntimePolicy {

    fun initial(component: EmbeddedComponent, payloadReady: Boolean, version: String = ""): ComponentRuntime =
        ComponentRuntime(
            component = component,
            state = if (payloadReady) EmbeddedProcessState.STOPPED else EmbeddedProcessState.NOT_INSTALLED,
            payloadReady = payloadReady,
            payloadVersion = version,
            message = if (payloadReady) "Stopped" else "Runtime payload not installed",
        )

    /**
     * Apply one event and return the next state plus the actions to execute.
     *
     * A `STOPPING` state with a live pid always implies a terminate action, so
     * no branch can leave a process running that the state machine believes is
     * going away.
     */
    fun decide(state: ComponentRuntime, event: RuntimeEvent, config: PolicyConfig): PolicyResult {
        val result = when (event) {
            is RuntimeEvent.StartRequested -> resultOf(onStart(state, event))
            is RuntimeEvent.StopRequested -> resultOf(onStop(state, event))
            is RuntimeEvent.PayloadResolved -> resultOf(onPayloadResolved(state, event))
            is RuntimeEvent.PayloadMissing -> resultOf(onPayloadMissing(state, event))
            is RuntimeEvent.ProcessStarted -> resultOf(onProcessStarted(state, event))
            is RuntimeEvent.HealthOk -> resultOf(onHealthOk(state, event))
            is RuntimeEvent.HealthFailed -> resultOf(onHealthFailed(state, event, config))
            is RuntimeEvent.ProcessExited -> onProcessExited(state, event, config)
            is RuntimeEvent.Activity -> resultOf(state.copy(lastActivityAt = event.at))
            is RuntimeEvent.Tick -> onTick(state, event, config)
            is RuntimeEvent.LowMemory -> onLowMemory(state, event)
        }
        return result.ensureTerminationAction()
    }

    /**
     * The supervisor persists [PolicyResult.next] after every event, so branches
     * that only change state need no extra bookkeeping action.
     */
    private fun resultOf(next: ComponentRuntime): PolicyResult = PolicyResult(next, emptyList())

    private fun PolicyResult.ensureTerminationAction(): PolicyResult {
        if (next.state != EmbeddedProcessState.STOPPING || next.pid == null) return this
        if (actions.any { it is RuntimeAction.TerminateGracefully || it is RuntimeAction.TerminateForcefully }) {
            return this
        }
        return copy(actions = actions + RuntimeAction.TerminateGracefully)
    }

    private fun onStart(state: ComponentRuntime, event: RuntimeEvent.StartRequested): ComponentRuntime {
        if (!state.payloadReady) {
            return state.copy(
                desired = DesiredState.RUNNING,
                state = EmbeddedProcessState.NOT_INSTALLED,
                gaveUp = false,
                consecutiveFailures = 0,
                message = "Runtime payload not installed — build or import it on this device first",
            )
        }
        return state.copy(
            desired = DesiredState.RUNNING,
            state = EmbeddedProcessState.STARTING,
            gaveUp = false,
            consecutiveFailures = 0,
            nextRetryAt = 0,
            stopDeadlineAt = 0,
            lastActivityAt = event.at,
            message = "Starting ${state.component.label} locally",
        )
    }

    private fun onStop(state: ComponentRuntime, event: RuntimeEvent.StopRequested): ComponentRuntime =
        if (state.pid == null) {
            state.copy(
                desired = DesiredState.STOPPED,
                state = if (state.payloadReady) EmbeddedProcessState.STOPPED else EmbeddedProcessState.NOT_INSTALLED,
                nextRetryAt = 0,
                stopDeadlineAt = 0,
                spawnDeadlineAt = 0,
                gaveUp = false,
                healthDetail = null,
                message = if (event.reason == "idle") "Stopped after idle timeout" else "Stopped",
            )
        } else {
            state.copy(
                desired = DesiredState.STOPPED,
                state = EmbeddedProcessState.STOPPING,
                nextRetryAt = 0,
                message = "Stopping ${state.component.label}",
            )
        }

    private fun onPayloadResolved(state: ComponentRuntime, event: RuntimeEvent.PayloadResolved): ComponentRuntime =
        state.copy(
            payloadReady = true,
            payloadVersion = event.version,
            state = if (state.state == EmbeddedProcessState.NOT_INSTALLED) {
                EmbeddedProcessState.STOPPED
            } else {
                state.state
            },
            message = if (state.pid == null) "Payload ${event.version} installed" else state.message,
        )

    private fun onPayloadMissing(state: ComponentRuntime, event: RuntimeEvent.PayloadMissing): ComponentRuntime =
        state.copy(
            payloadReady = false,
            state = EmbeddedProcessState.NOT_INSTALLED,
            pid = null,
            stopDeadlineAt = 0,
            spawnDeadlineAt = 0,
            nextRetryAt = 0,
            message = event.message,
        )

    private fun onProcessStarted(state: ComponentRuntime, event: RuntimeEvent.ProcessStarted): ComponentRuntime {
        if (state.desired == DesiredState.STOPPED) {
            // The user changed their mind while the process was spawning.
            return state.copy(
                pid = event.pid,
                state = EmbeddedProcessState.STOPPING,
                message = "Stopping ${state.component.label}",
            )
        }
        return state.copy(
            pid = event.pid,
            state = EmbeddedProcessState.STARTING,
            startedAt = event.at,
            healthFailures = 0,
            message = "Waiting for ${state.component.label} to answer on loopback",
        )
    }

    private fun onHealthOk(state: ComponentRuntime, event: RuntimeEvent): ComponentRuntime = state.copy(
        state = EmbeddedProcessState.RUNNING,
        healthFailures = 0,
        consecutiveFailures = 0,
        restartCount = 0,
        lastHealthyAt = event.at,
        lastActivityAt = if (state.lastActivityAt == 0L) event.at else state.lastActivityAt,
        healthDetail = null,
        message = "Running locally",
    )

    private fun onHealthFailed(
        state: ComponentRuntime,
        event: RuntimeEvent.HealthFailed,
        config: PolicyConfig,
    ): ComponentRuntime {
        if (state.pid == null) return state.copy(healthDetail = event.detail)
        val failures = state.healthFailures + 1
        val degraded = failures >= config.healthFailureTolerance
        return state.copy(
            healthFailures = failures,
            state = if (degraded) EmbeddedProcessState.DEGRADED else state.state,
            healthDetail = event.detail,
            message = if (degraded) {
                "Process alive but not answering on loopback"
            } else {
                "Health check failed (${failures}/${config.healthFailureTolerance})"
            },
        )
    }

    private fun onProcessExited(
        state: ComponentRuntime,
        event: RuntimeEvent.ProcessExited,
        config: PolicyConfig,
    ): PolicyResult {
        if (state.desired == DesiredState.STOPPED || event.expected) {
            val stopped = state.copy(
                pid = null,
                state = if (state.payloadReady) EmbeddedProcessState.STOPPED else EmbeddedProcessState.NOT_INSTALLED,
                lastExitCode = event.exitCode,
                healthFailures = 0,
                consecutiveFailures = 0,
                restartCount = 0,
                nextRetryAt = 0,
                stopDeadlineAt = 0,
                spawnDeadlineAt = 0,
                gaveUp = false,
                healthDetail = null,
                message = "Stopped",
            )
            return PolicyResult(stopped, emptyList())
        }

        val failures = state.consecutiveFailures + 1
        val restarts = state.restartCount + 1
        val exitNote = event.exitCode?.let { "exit code $it" } ?: "terminated"

        if (failures > config.maxRestarts) {
            val failed = state.copy(
                pid = null,
                state = EmbeddedProcessState.FAILED,
                consecutiveFailures = failures,
                restartCount = restarts,
                lastExitCode = event.exitCode,
                gaveUp = true,
                nextRetryAt = 0,
                stopDeadlineAt = 0,
                spawnDeadlineAt = 0,
                healthDetail = null,
                message = "${state.component.label} crashed $failures times ($exitNote); stopped retrying",
            )
            return PolicyResult(failed, emptyList())
        }

        val backoff = backoffMs(failures, config)
        val retrying = state.copy(
            pid = null,
            state = EmbeddedProcessState.FAILED,
            consecutiveFailures = failures,
            restartCount = restarts,
            lastExitCode = event.exitCode,
            nextRetryAt = event.at + backoff,
            stopDeadlineAt = 0,
            spawnDeadlineAt = 0,
            healthDetail = null,
            message = "${state.component.label} $exitNote; restarting in ${backoff / 1000}s",
        )
        return PolicyResult(retrying, emptyList())
    }

    private fun onLowMemory(state: ComponentRuntime, event: RuntimeEvent.LowMemory): PolicyResult {
        val shouldStop = event.critical &&
            state.component.priority > EmbeddedComponent.N8N.priority &&
            state.state.isActive
        if (!shouldStop) return PolicyResult(state, emptyList())
        val stopped = state.copy(
            desired = DesiredState.STOPPED,
            state = EmbeddedProcessState.STOPPING,
            message = "Stopping ${state.component.label}: device memory critical (${event.availMb} MiB free)",
        )
        return PolicyResult(stopped, listOf(RuntimeAction.TerminateGracefully))
    }

    private fun onTick(state: ComponentRuntime, event: RuntimeEvent.Tick, config: PolicyConfig): PolicyResult {
        val actions = mutableListOf<RuntimeAction>()
        var next = state

        // 1. Restart after a crash, while the user still wants the runtime up.
        val retryDue = next.desired == DesiredState.RUNNING &&
            next.pid == null &&
            !next.gaveUp &&
            next.payloadReady &&
            (next.nextRetryAt == 0L || event.at >= next.nextRetryAt) &&
            next.state != EmbeddedProcessState.STARTING &&
            next.state != EmbeddedProcessState.STOPPING

        if (retryDue) {
            next = next.copy(
                state = EmbeddedProcessState.STARTING,
                nextRetryAt = 0,
                message = "Starting ${next.component.label} locally",
            )
            actions += RuntimeAction.Spawn
        }

        // 2. A process that never became healthy must not linger forever.
        val spawnTimedOut = next.pid != null &&
            next.state == EmbeddedProcessState.STARTING &&
            next.spawnDeadlineAt > 0 &&
            event.at >= next.spawnDeadlineAt

        if (spawnTimedOut) {
            next = next.copy(
                state = EmbeddedProcessState.STOPPING,
                message = "${next.component.label} did not become healthy; restarting",
            )
            actions += RuntimeAction.TerminateForcefully
        }

        // 3. Force-kill a process that ignored the graceful stop.
        val stopTimedOut = next.pid != null &&
            next.state == EmbeddedProcessState.STOPPING &&
            next.stopDeadlineAt > 0 &&
            event.at >= next.stopDeadlineAt

        if (stopTimedOut) {
            actions += RuntimeAction.TerminateForcefully
        }

        // 4. Idle auto-stop keeps RAM free on phones.
        val idleSince = maxOf(next.lastActivityAt, next.lastHealthyAt, next.startedAt)
        val idleTimedOut = config.idleStopMs > 0 &&
            next.state == EmbeddedProcessState.RUNNING &&
            next.desired == DesiredState.RUNNING &&
            idleSince > 0 &&
            event.at - idleSince >= config.idleStopMs

        if (idleTimedOut) {
            next = next.copy(
                desired = DesiredState.STOPPED,
                state = EmbeddedProcessState.STOPPING,
                message = "Stopping ${next.component.label} after ${config.idleStopMs / 60_000} min idle",
            )
            actions += RuntimeAction.TerminateGracefully
        }

        return PolicyResult(next, actions)
    }

    fun backoffMs(failures: Int, config: PolicyConfig): Long {
        val exponent = (failures - 1).coerceIn(0, 16)
        val delay = config.baseBackoffMs shl exponent
        return delay.coerceAtMost(config.maxBackoffMs)
    }
}
