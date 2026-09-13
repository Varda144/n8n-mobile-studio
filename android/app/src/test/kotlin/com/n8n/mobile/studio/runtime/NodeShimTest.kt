package com.n8n.mobile.studio.runtime

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Tests the `node` shim the app writes into its own `bin/` directory.
 *
 * The app starts the engine itself, but n8n spawns helpers (task runners, code
 * nodes) that look the engine up by name. Android will not execute a script kept
 * in app storage, so the shim is a `/system/bin/sh` script that execs the engine
 * from the directory where Android does allow execution. Both halves of that
 * contract matter: the engine path must be absolute and correct, and every
 * argument must reach the engine unchanged.
 */
class NodeShimTest {

    private fun runtimeTree(name: String): RuntimePaths {
        val dir = File(System.getProperty("java.io.tmpdir"), "n8n-shim-$name-${System.nanoTime()}")
        dir.deleteRecursively()
        check(RuntimePaths(dir).ensure().isSuccess)
        return RuntimePaths(dir)
    }

    private fun hostEngine(): File? =
        listOf("/usr/local/bin/node", "/usr/bin/node", "/opt/homebrew/bin/node")
            .map(::File)
            .firstOrNull { it.canExecute() }

    @Test
    fun `the shim is written executable and points at the engine by absolute path`() {
        val paths = runtimeTree("write")
        val engine = File(paths.payloads, "engine/libnode.so").apply {
            parentFile?.mkdirs()
            writeText("not a real engine")
            setExecutable(true)
        }

        val shim = assertNotNull(paths.ensureNodeShim(engine), "shim could not be written")
        assertEquals(paths.nodeShim().absolutePath, shim.absolutePath)
        assertTrue(shim.canExecute(), "the shim must be executable")
        val text = shim.readText()
        assertTrue(
            text.contains(engine.absolutePath),
            "the shim must exec the packaged engine by absolute path:\n$text",
        )
        assertTrue(
            text.startsWith("#!/system/bin/sh"),
            "Android will not execute the engine directly, so the shim needs the system shell",
        )
        assertTrue(text.contains("\"\$@\""), "the shim must forward every argument:\n$text")

        // Stable: rewriting it must not add another line.
        assertEquals(text, assertNotNull(paths.ensureNodeShim(engine)).readText())
    }

    @Test
    fun `anything spawned through the shim runs the engine with the given arguments`() {
        val engine = hostEngine() ?: run {
            println("skipped: no host node to act as the packaged engine")
            return
        }
        val paths = runtimeTree("exec")
        val shim = assertNotNull(paths.ensureNodeShim(engine))

        // The shebang targets Android's shell, so the test runs the body with the
        // host shell: that is what proves the exec line and argument forwarding.
        val process = ProcessBuilder("/bin/sh", shim.absolutePath, "-p", "process.pid")
            .directory(paths.tmp)
            .redirectErrorStream(true)
            .apply { environment()["PATH"] = paths.bin.absolutePath + ":" + System.getenv("PATH") }
            .start()

        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "shim did not finish")
        assertEquals(0, process.exitValue(), "shim failed: $output")
        assertTrue(
            output.trim().toLongOrNull()?.let { it > 0 } == true,
            "the shim must run the engine with the arguments it was given, got: $output",
        )
    }
}
