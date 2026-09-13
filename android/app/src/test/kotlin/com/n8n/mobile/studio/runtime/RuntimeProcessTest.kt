package com.n8n.mobile.studio.runtime

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Exercises the process layer with a real child process.
 *
 * On a phone the child is the packaged Node engine; here it is `/bin/sh`, but the
 * code path is the same one the app uses: launch, pid tracking, output pumping,
 * log writing, exit reporting. That path is what fills the log view and the
 * terminal, and it is the part a device test would otherwise be the first to
 * exercise.
 */
class RuntimeProcessTest {

    private fun workDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "n8n-runtime-$name-${System.nanoTime()}")
        dir.deleteRecursively()
        check(RuntimePaths(dir).ensure().isSuccess) { "cannot create test runtime tree at $dir" }
        return dir
    }

    /**
     * A stand-in for the packaged engine. A real runtime is started with a
     * preloaded script that publishes its pid ([LaunchSpec.publishesPid]); the
     * shell equivalent is `echo $$ > pidfile`, which proves the same path.
     */
    private fun specFor(paths: RuntimePaths, script: String, publishesPid: Boolean = false): LaunchSpec =
        LaunchSpec(
            component = EmbeddedComponent.N8N,
            executable = File("/bin/sh"),
            args = if (publishesPid) {
                listOf("-c", "echo \$\$ > \"$$PID_FILE_ENV\"; $script")
            } else {
                listOf("-c", script)
            },
            workingDir = paths.tmp,
            env = mapOf(PID_FILE_ENV to paths.pidFile(EmbeddedComponent.N8N).absolutePath),
            logFile = paths.logFile(EmbeddedComponent.N8N),
            pidFile = paths.pidFile(EmbeddedComponent.N8N),
            label = "test-shell",
            publishesPid = publishesPid,
        )

    private companion object {
        const val N8N_STUDIO_PIDFILE = "N8N_STUDIO_PIDFILE"

        /** How the app learns a payload's pid when the platform will not say. */
        const val PID_FILE_ENV = "N8N_STUDIO_PIDFILE"
    }

    private class Recorder {
        val events = CopyOnWriteArrayList<ProcessEvent>()
        val exited = CountDownLatch(1)

        fun onEvent(event: ProcessEvent) {
            events += event
            if (event is ProcessEvent.Exited) exited.countDown()
        }

        fun output(): List<String> = events.filterIsInstance<ProcessEvent.Output>().map { it.line }
    }

    @Test
    fun `child output reaches both the log file and the event stream`() {
        val paths = RuntimePaths(workDir("output"))
        val recorder = Recorder()
        val spec = specFor(paths, "echo first; sleep 0.2; echo second")

        val process = RuntimeProcess.launch(spec, recorder::onEvent).getOrThrow()
        assertTrue(recorder.exited.await(20, TimeUnit.SECONDS), "child process did not exit")
        process.waitForExit(5_000)

        val log = spec.logFile.readText()
        val events = recorder.output()
        listOf("first", "second").forEach { expected ->
            assertTrue(log.contains(expected), "log file is missing '$expected'.\nLog:\n$log")
            assertTrue(
                events.any { it.contains(expected) },
                "no ProcessEvent.Output for '$expected' — the log view and terminal would stay empty while the runtime runs. Events: $events",
            )
        }

        // The pid itself is covered by the tests below (a payload that publishes
        // it) and by PidPreloadTest; here the point is that the launch is reported
        // with the command line the log and the notification show.
        val started = assertNotNull(
            recorder.events.filterIsInstance<ProcessEvent.Started>().firstOrNull(),
            "no Started event",
        )
        assertTrue(
            started.commandLine.startsWith("test-shell") && started.commandLine.contains("echo first"),
            "the Started event must describe what was launched: ${started.commandLine}",
        )
    }

    @Test
    fun `a long running child keeps its pid alive and reports its exit`() {
        val paths = RuntimePaths(workDir("longrun"))
        val recorder = Recorder()
        val spec = specFor(paths, "echo ready; sleep 30", publishesPid = true)

        val process = RuntimeProcess.launch(spec, recorder::onEvent).getOrThrow()
        val pid = assertNotNull(process.pid, "a payload that publishes its pid must be reported with it")
        assertTrue(RuntimeProcess.isAlive(pid), "pid $pid is not alive right after launch")
        assertTrue(
            RuntimeProcess.cmdline(pid).orEmpty().contains("/bin/sh"),
            "the pid does not belong to the launched command: ${RuntimeProcess.cmdline(pid)}",
        )

        // The log must be readable while the process is still running: the user
        // opens the log view long before the runtime stops.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !spec.logFile.readText().contains("ready")) {
            Thread.sleep(50)
        }
        assertTrue(spec.logFile.readText().contains("ready"), "live log did not receive output")

        process.terminate()
        assertTrue(process.waitForExit(10_000), "child ignored terminate()")
        assertTrue(recorder.exited.await(10, TimeUnit.SECONDS), "no Exited event after terminate()")
        assertNotNull(recorder.events.filterIsInstance<ProcessEvent.Exited>().first().exitCode)
    }

    @Test
    fun `a missing executable fails the launch instead of reporting success`() {
        val paths = RuntimePaths(workDir("missing"))
        val spec = specFor(paths, "true").copy(executable = File(paths.bin, "definitely-not-here"))

        val result = RuntimeProcess.launch(spec) { }
        assertTrue(result.isFailure, "launching a non-existent executable must fail")
    }

    @Test
    fun `the log is rotated before it can grow without bound`() {
        val paths = RuntimePaths(workDir("rotate"))
        val spec = specFor(paths, "true")
        spec.logFile.parentFile?.mkdirs()
        spec.logFile.writeText("x".repeat(5 * 1024 * 1024))

        val recorder = Recorder()
        RuntimeProcess.launch(spec, recorder::onEvent).getOrThrow()
        assertTrue(recorder.exited.await(20, TimeUnit.SECONDS))

        val rotated = File(spec.logFile.parentFile, spec.logFile.name + ".1")
        assertTrue(rotated.isFile, "an oversized log must be rotated, not reused")
        assertTrue(spec.logFile.length() < 1024 * 1024, "the new log should start small")
    }

    @Test
    fun `pid files are parsed defensively`() {
        val paths = RuntimePaths(workDir("pidfile"))
        val pidFile = paths.pidFile(EmbeddedComponent.N8N)
        pidFile.parentFile?.mkdirs()

        pidFile.writeText("")
        assertEquals(null, RuntimeProcess.readPid(pidFile), "an empty pid file is not a pid")
        pidFile.writeText("not-a-number\n")
        assertEquals(null, RuntimeProcess.readPid(pidFile), "garbage is not a pid")
        pidFile.writeText("-4\n")
        assertEquals(null, RuntimeProcess.readPid(pidFile), "a negative pid is not a pid")
        pidFile.writeText("  ${ProcessHandle.current().pid()} \n")
        assertEquals(
            ProcessHandle.current().pid(),
            RuntimeProcess.readPid(pidFile),
            "a real pid with surrounding whitespace must parse",
        )
    }
}
