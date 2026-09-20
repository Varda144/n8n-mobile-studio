package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Pins the two things about the supervisor that only the device used to reveal:
 * that it never corrupts the pid file it shares with the running engine, and that
 * a runtime which refuses to run comes back with the reason it refused.
 *
 * The failures these tests describe are the ones a phone actually produces — a
 * linker that rejects the payload, an OS that denies execution, a crash after
 * migrations — and none of them are visible to the user unless the supervisor puts
 * them into the status the GUI and the notification read.
 */
class RuntimeSupervisorTest {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "supervisor-test").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val workDir = File(
        System.getProperty("java.io.tmpdir"),
        "n8n-supervisor-test-${System.nanoTime()}",
    ).apply { mkdirs() }

    @After
    fun tearDown() {
        scope.cancel()
        dispatcher.close()
        workDir.deleteRecursively()
    }

    // ------------------------------------------------------------------ fixtures

    private fun pidFile(component: EmbeddedComponent) = File(workDir, "${component.id}.pid")

    private fun specFor(component: EmbeddedComponent) = ComponentSpec(
        component = component,
        enabled = true,
        packaged = true,
        version = "2.38.7",
        port = 5678,
        healthPath = "/healthz",
        guiPath = "/",
        memoryMb = 512,
        entryHint = "bin/n8n",
        integrity = "sha256:test",
    )

    private fun launchFor(component: EmbeddedComponent) = LaunchSpec(
        component = component,
        executable = File(workDir, "libnode.so"),
        args = listOf("bin/n8n", "start"),
        workingDir = workDir,
        env = mapOf("N8N_PORT" to "5678"),
        logFile = File(workDir, "${component.id}.log"),
        pidFile = pidFile(component),
        label = component.label,
        publishesPid = true,
    )

    private class FakeRuntime(
        override val component: EmbeddedComponent,
        private val descriptor: ComponentSpec,
        private val launch: LaunchSpec,
    ) : EmbeddedRuntime {
        override fun spec(): ComponentSpec = descriptor

        override suspend fun prepare(): Result<PreparedRuntime> = Result.success(
            PreparedRuntime(
                component = component,
                launch = launch,
                endpoint = "http://127.0.0.1:${descriptor.port}",
                version = descriptor.version,
                payloadDir = launch.workingDir,
            ),
        )
    }

    /** A process that is already gone; the supervisor only reads its exit code. */
    private class DeadProcess(override val pid: Long?, private val code: Int?) : SpawnedProcess {
        override fun isAlive(): Boolean = false

        override fun terminate() = Unit

        override fun kill() = Unit

        override fun waitForExit(timeoutMs: Long): Boolean = true

        override fun exitCode(): Int? = code
    }

    private fun build(spawner: ProcessSpawner): Pair<RuntimeSupervisor, RuntimeState> {
        val state = RuntimeState()
        val supervisor = RuntimeSupervisor(
            scope = scope,
            state = state,
            logs = RuntimeLogBuffer(),
            runtimeFor = { component -> FakeRuntime(component, specFor(component), launchFor(component)) },
            config = { AppConfig() },
            spawner = spawner,
            health = HealthProber { url, _ ->
                RuntimeStatus(reachable = false, detail = "not probed by this test", checkedAt = 0, url = url)
            },
            clock = { 1_000L },
        )
        supervisor.pidFiles { component -> pidFile(component) }
        return supervisor to state
    }

    private fun awaitStatus(
        state: RuntimeState,
        component: EmbeddedComponent,
        predicate: (EmbeddedComponentStatus) -> Boolean,
    ): EmbeddedComponentStatus {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val status = state.status(component)
            if (predicate(status)) return status
            Thread.sleep(5)
        }
        throw AssertionError("timed out waiting for status; last = ${state.status(component)}")
    }

    private suspend fun startWithPayload(supervisor: RuntimeSupervisor) {
        supervisor.refreshPayload(EmbeddedComponent.N8N, "2.38.7")
        supervisor.startComponent(EmbeddedComponent.N8N)
    }

    // --------------------------------------------------------------------- tests

    @Test
    fun `the pid the payload published survives the supervisor's own bookkeeping`() = runBlocking {
        val published = 4242L
        val spawner = ProcessSpawner { launch, onEvent ->
            // Exactly what the packaged preload does before the engine gets to work.
            launch.pidFile.parentFile?.mkdirs()
            launch.pidFile.writeText(published.toString())
            onEvent(ProcessEvent.Started(published, launch.toString()))
            Result.success(DeadProcess(published, null))
        }
        val (supervisor, state) = build(spawner)

        startWithPayload(supervisor)
        awaitStatus(state, EmbeddedComponent.N8N) { it.pid == published }

        // The pid file is the only way back to a runtime that outlives the app, so
        // it must keep a value the app can actually use.
        assertEquals(published, RuntimeProcess.readPid(pidFile(EmbeddedComponent.N8N)))
        assertEquals(published.toString(), pidFile(EmbeddedComponent.N8N).readText().trim())
    }

    @Test
    fun `a runtime that dies before answering reports what it last said`() = runBlocking {
        val spawner = ProcessSpawner { launch, onEvent ->
            onEvent(ProcessEvent.Started(5150L, launch.toString()))
            onEvent(
                ProcessEvent.Output(
                    "CANNOT LINK EXECUTABLE \"libnode.so\": library \"libc++_shared.so\" not found",
                    1_000,
                ),
            )
            onEvent(ProcessEvent.Exited(1, 1_100))
            Result.success(DeadProcess(5150L, 1))
        }
        val (supervisor, state) = build(spawner)

        startWithPayload(supervisor)
        val failed = awaitStatus(state, EmbeddedComponent.N8N) {
            it.state == EmbeddedProcessState.FAILED
        }

        val detail = failed.healthDetail ?: ""
        assertTrue(
            detail.contains("CANNOT LINK EXECUTABLE"),
            "the status must carry the reason the process died, was: $detail",
        )
        assertTrue(detail.contains("last output:"), "the detail must say where it came from, was: $detail")
    }

    @Test
    fun `a runtime the OS refuses to start reports the launch failure`() = runBlocking {
        val spawner = ProcessSpawner { _, _ ->
            Result.failure(IOException("Cannot run program \"libnode.so\": error=13, Permission denied"))
        }
        val (supervisor, state) = build(spawner)

        startWithPayload(supervisor)
        val failed = awaitStatus(state, EmbeddedComponent.N8N) {
            it.state == EmbeddedProcessState.FAILED
        }

        val detail = failed.healthDetail
        assertTrue(
            detail != null && detail.contains("Permission denied"),
            "a launch the OS denied must say so, was: $detail",
        )
    }

    @Test
    fun `a silent crash keeps the policy message instead of inventing a detail`() = runBlocking {
        val spawner = ProcessSpawner { launch, onEvent ->
            onEvent(ProcessEvent.Started(6161L, launch.toString()))
            onEvent(ProcessEvent.Exited(1, 1_100))
            Result.success(DeadProcess(6161L, 1))
        }
        val (supervisor, state) = build(spawner)

        startWithPayload(supervisor)
        val failed = awaitStatus(state, EmbeddedComponent.N8N) {
            it.state == EmbeddedProcessState.FAILED
        }

        assertTrue(failed.message.contains("exit code 1"), "was: ${failed.message}")
        assertNull(failed.healthDetail, "nothing was said, so nothing should be quoted")
    }
}
