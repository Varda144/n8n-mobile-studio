package com.n8n.mobile.studio.runtime

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Pins the lifecycle behaviour both runtimes share.
 *
 * These tests are the contract behind "start/stop/restart, background execution,
 * low-memory friendly supervision": crashes restart with exponential backoff and
 * then give up loudly, a process is only RUNNING once it answers on loopback, a
 * stopping process is killed if it ignores SIGTERM, and idle runtimes release RAM.
 */
class RuntimePolicyTest {

    private val config = PolicyConfig(
        maxRestarts = 3,
        baseBackoffMs = 1_000,
        maxBackoffMs = 8_000,
        healthFailureTolerance = 3,
        spawnTimeoutMs = 60_000,
        stopGraceMs = 5_000,
        idleStopMs = 600_000,
    )

    private fun ready(): ComponentRuntime =
        RuntimePolicy.initial(EmbeddedComponent.N8N, payloadReady = true, version = "2.38.7")

    @Test
    fun `start without a payload reports NOT_INSTALLED and never spawns`() {
        val state = RuntimePolicy.initial(EmbeddedComponent.N8N, payloadReady = false)
        val result = RuntimePolicy.decide(state, RuntimeEvent.StartRequested(1_000), config)

        assertEquals(EmbeddedProcessState.NOT_INSTALLED, result.next.state)
        assertEquals(DesiredState.RUNNING, result.next.desired)
        assertTrue(result.actions.isEmpty(), "must not spawn without a payload")
        assertTrue(result.next.message.contains("not installed"))
    }

    @Test
    fun `start with a payload transitions to STARTING and spawns`() {
        val result = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(1_000), config)

        assertEquals(EmbeddedProcessState.STARTING, result.next.state)
        assertEquals(listOf(RuntimeAction.Spawn), result.actions)
    }

    @Test
    fun `process is only RUNNING after a successful health probe`() {
        val started = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        val spawned = RuntimePolicy.decide(started, RuntimeEvent.ProcessStarted(500, 4242), config).next

        assertEquals(EmbeddedProcessState.STARTING, spawned.state)
        assertEquals(4242L, spawned.pid)

        val running = RuntimePolicy.decide(spawned, RuntimeEvent.HealthOk(1_500), config).next
        assertEquals(EmbeddedProcessState.RUNNING, running.state)
        assertEquals(0, running.healthFailures)
    }

    @Test
    fun `repeated health failures degrade instead of killing a live process`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(100, 7), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.HealthOk(200), config).next

        repeat(3) { index ->
            state = RuntimePolicy.decide(
                state,
                RuntimeEvent.HealthFailed(300L + index, "connection refused"),
                config,
            ).next
        }

        assertEquals(EmbeddedProcessState.DEGRADED, state.state)
        assertEquals(7L, state.pid)
        assertTrue(state.message.contains("not answering"))
    }

    @Test
    fun `crash restarts with backoff and then gives up`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next

        var now = 10_000L
        repeat(3) { attempt ->
            state = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(now, 100L + attempt), config).next
            state = RuntimePolicy.decide(state, RuntimeEvent.ProcessExited(now + 10, exitCode = 1), config).next
            assertEquals(EmbeddedProcessState.FAILED, state.state)
            assertTrue(state.nextRetryAt > now + 10, "retry must be scheduled in the future")
            now = state.nextRetryAt
        }

        // The retry is due: the supervisor must spawn again.
        val retry = RuntimePolicy.decide(state, RuntimeEvent.Tick(now), config)
        assertTrue(retry.actions.contains(RuntimeAction.Spawn))
        assertEquals(EmbeddedProcessState.STARTING, retry.next.state)

        // Fourth crash exceeds maxRestarts: give up loudly.
        var failing = RuntimePolicy.decide(retry.next, RuntimeEvent.ProcessStarted(now + 1, 500), config).next
        failing = RuntimePolicy.decide(failing, RuntimeEvent.ProcessExited(now + 2, exitCode = 137), config).next
        assertTrue(failing.gaveUp)
        assertTrue(failing.message.contains("stopped retrying"), failing.message)

        val afterGivingUp = RuntimePolicy.decide(failing, RuntimeEvent.Tick(now + 600_000), config)
        assertTrue(afterGivingUp.actions.isEmpty(), "a given-up runtime must not be respawned by ticks")
    }

    @Test
    fun `backoff grows exponentially and is capped`() {
        assertEquals(1_000L, RuntimePolicy.backoffMs(1, config))
        assertEquals(2_000L, RuntimePolicy.backoffMs(2, config))
        assertEquals(4_000L, RuntimePolicy.backoffMs(3, config))
        assertEquals(8_000L, RuntimePolicy.backoffMs(4, config))
        assertEquals(8_000L, RuntimePolicy.backoffMs(9, config))
    }

    @Test
    fun `stop terminates gracefully then force-kills the stragglers`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(10, 99), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.HealthOk(20), config).next

        val stopping = RuntimePolicy.decide(state, RuntimeEvent.StopRequested(30, "user"), config)
        assertEquals(EmbeddedProcessState.STOPPING, stopping.next.state)
        assertTrue(stopping.actions.contains(RuntimeAction.TerminateGracefully))

        val grace = stopping.next.copy(stopDeadlineAt = 5_030)
        val forced = RuntimePolicy.decide(grace, RuntimeEvent.Tick(6_000), config)
        assertTrue(forced.actions.contains(RuntimeAction.TerminateForcefully))
    }

    @Test
    fun `a stop requested while spawning still terminates the new process`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.StopRequested(10), config).next
        val late = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(20, 555), config)

        assertEquals(EmbeddedProcessState.STOPPING, late.next.state)
        assertTrue(
            late.actions.contains(RuntimeAction.TerminateGracefully),
            "a process that appears after stop must not be left running",
        )
    }

    @Test
    fun `idle runtime is stopped to free memory`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(10, 42), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.HealthOk(1_000), config).next
        state = state.copy(lastActivityAt = 2_000, startedAt = 1_000)

        val early = RuntimePolicy.decide(state, RuntimeEvent.Tick(3_000), config)
        assertTrue(early.actions.isEmpty(), "must not stop while the runtime is young")

        val idled = RuntimePolicy.decide(state, RuntimeEvent.Tick(2_000 + config.idleStopMs + 1), config)
        assertEquals(EmbeddedProcessState.STOPPING, idled.next.state)
        assertTrue(idled.actions.contains(RuntimeAction.TerminateGracefully))
        assertTrue(idled.actions.none { it == RuntimeAction.Spawn })
    }

    @Test
    fun `a process that never becomes healthy is killed`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(100, 31), config).next
        state = state.copy(spawnDeadlineAt = 60_000)

        val killed = RuntimePolicy.decide(state, RuntimeEvent.Tick(60_001), config)
        assertTrue(killed.actions.contains(RuntimeAction.TerminateForcefully))
        assertEquals(EmbeddedProcessState.STOPPING, killed.next.state)
    }

    @Test
    fun `critical memory pressure stops the lower priority runtime only`() {
        val openCode = RuntimePolicy.initial(EmbeddedComponent.OPENCODE, payloadReady = true)
            .copy(pid = 123, state = EmbeddedProcessState.RUNNING, desired = DesiredState.RUNNING)
        val n8n = RuntimePolicy.initial(EmbeddedComponent.N8N, payloadReady = true)
            .copy(pid = 456, state = EmbeddedProcessState.RUNNING, desired = DesiredState.RUNNING)

        val stopped = RuntimePolicy.decide(
            openCode,
            RuntimeEvent.LowMemory(1_000, availMb = 120, critical = true),
            config,
        )
        assertTrue(stopped.actions.contains(RuntimeAction.TerminateGracefully))
        assertEquals(DesiredState.STOPPED, stopped.next.desired)

        val untouched = RuntimePolicy.decide(
            n8n,
            RuntimeEvent.LowMemory(1_000, availMb = 120, critical = true),
            config,
        )
        assertTrue(untouched.actions.isEmpty(), "n8n is the primary surface and stays up")
        assertFalse(untouched.next.state == EmbeddedProcessState.STOPPING)
    }

    @Test
    fun `expected exit after a user stop does not restart`() {
        var state = RuntimePolicy.decide(ready(), RuntimeEvent.StartRequested(0), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.ProcessStarted(10, 5), config).next
        state = RuntimePolicy.decide(state, RuntimeEvent.StopRequested(20), config).next

        val exited = RuntimePolicy.decide(state, RuntimeEvent.ProcessExited(30, exitCode = 0), config).next
        assertEquals(EmbeddedProcessState.STOPPED, exited.state)
        assertEquals(0, exited.consecutiveFailures)
        assertEquals(0L, exited.nextRetryAt)

        val tick = RuntimePolicy.decide(exited, RuntimeEvent.Tick(120_000), config)
        assertTrue(tick.actions.isEmpty())
    }
}
