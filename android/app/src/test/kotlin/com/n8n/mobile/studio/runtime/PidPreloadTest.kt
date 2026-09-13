package com.n8n.mobile.studio.runtime

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Tests the mechanism the app relies on to know a runtime's pid.
 *
 * Java cannot read a child process's pid on every Android release, so the engine
 * is started with `node --require <preload> <entry>`: the preload writes the pid
 * the app then uses for status, orphan reclaiming and stop. Two things can break
 * silently here — a preload that does not write the file, and a preload that
 * changes the command line the payload sees — so both are asserted, using the
 * Node.js that is present on the build machine when it is available.
 */
class PidPreloadTest {

    private fun runtimeTree(name: String): RuntimePaths {
        val dir = File(System.getProperty("java.io.tmpdir"), "n8n-pid-$name-${System.nanoTime()}")
        dir.deleteRecursively()
        check(RuntimePaths(dir).ensure().isSuccess)
        return RuntimePaths(dir)
    }

    private fun nodeBinary(): File? =
        listOf("/usr/local/bin/node", "/usr/bin/node", "/opt/homebrew/bin/node")
            .map(::File)
            .firstOrNull { it.canExecute() }
            ?: System.getenv("PATH")?.split(File.pathSeparator)
                ?.map { File(it, "node") }
                ?.firstOrNull { it.canExecute() }

    @Test
    fun `the preload script is written once and is stable`() {
        val paths = runtimeTree("script")
        val script = assertNotNull(paths.ensurePidPreload(), "the preload could not be written")
        assertTrue(script.isFile, "preload missing at ${script.absolutePath}")
        assertEquals(RuntimePins.NODE_PID_PRELOAD, script.name)

        val first = script.readText()
        assertEquals(first, assertNotNull(paths.ensurePidPreload()).readText(), "preload was rewritten differently")
        assertTrue(first.contains("N8N_STUDIO_PIDFILE"), "the preload must read the pid file path from the environment")
        assertTrue(first.contains("process.pid"), "the preload must publish the real pid")
        // It must never be the reason a runtime fails to start.
        assertTrue(first.contains("catch"), "the preload must swallow its own failures")
    }

    @Test
    fun `a node entry started with the preload publishes its pid without changing argv`() {
        val node = nodeBinary() ?: run {
            println("skipped: no node on PATH to run the preload with")
            return
        }
        val paths = runtimeTree("node")
        val preload = assertNotNull(paths.ensurePidPreload())

        // A stand-in for n8n's CLI: reports the argv the payload sees.
        val entry = File(paths.projects, "fake-entry.js").apply {
            parentFile?.mkdirs()
            writeText("process.stdout.write('argv=' + JSON.stringify(process.argv.slice(1)) + '\\n')")
        }
        val pidFile = paths.pidFile(EmbeddedComponent.N8N)
        pidFile.delete()

        val process = ProcessBuilder(
            node.absolutePath,
            "--require", preload.absolutePath,
            entry.absolutePath,
            "start",
        )
            .directory(paths.projects)
            .redirectErrorStream(true)
            .apply { environment()["N8N_STUDIO_PIDFILE"] = pidFile.absolutePath }
            .start()

        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "node did not exit")
        assertEquals(0, process.exitValue(), "node failed: $output")

        val published = assertNotNull(
            RuntimeProcess.readPid(pidFile),
            "the preload did not write a pid (output: $output)",
        )
        assertTrue(published > 0)

        // The payload must still see `node <entry> <args>`: n8n's CLI parses argv.
        assertTrue(
            output.contains("\"start\"") && output.contains("fake-entry.js"),
            "argv changed when the preload was added: $output",
        )
        assertTrue(
            !output.contains("n8n-studio-pid.cjs"),
            "the preload must not be visible in the payload's argv: $output",
        )
    }
}
