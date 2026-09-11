package com.n8n.mobile.studio.runtime

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

/**
 * The installer is the boundary between "a payload exists" and "the app will
 * launch it". These tests cover the failure modes that would otherwise surface as
 * a mysterious half-working runtime on a phone: wrong digest, zip-slip entries,
 * missing entry script, interrupted installs and storage bloat.
 */
class RuntimeInstallerTest {

    private val root: File = Files.createTempDirectory("runtime-installer").toFile()
    private val paths = RuntimePaths.forFilesDir(root)
    private val installer = RuntimeInstaller(paths, now = { 1_700_000_000_000L })

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun install(
        payload: ByteArray,
        expectedSha256: String = Sha256.of(payload),
        version: String = "1.2.3",
        required: List<String> = listOf("n8n/bin/n8n"),
    ): InstalledPayload = installer.install(
        ByteArrayPayloadSource(payload, origin = "test"),
        InstallRequest(
            component = EmbeddedComponent.N8N,
            version = version,
            expectedSha256 = expectedSha256,
            requiredEntries = required,
        ),
    )

    @Test
    fun `installs a verified payload and writes a marker`() {
        val installed = install(zip("n8n/bin/n8n" to "#!/usr/bin/env node\n", "n8n/package.json" to "{}"))

        assertTrue(installed.entry.isFile, "entry script must exist after install")
        assertTrue(installed.marker.isFile, "install.json must be written")
        assertTrue(installed.isUsable())
        assertEquals("1.2.3", installed.version)
        assertEquals(paths.payloadDir(EmbeddedComponent.N8N, "1.2.3").canonicalPath, installed.dir.canonicalPath)
        assertEquals("1.2.3", paths.currentPointer(EmbeddedComponent.N8N).readText())
        assertNotNull(installer.installed(EmbeddedComponent.N8N))
    }

    @Test
    fun `rejects a payload whose digest does not match`() {
        val payload = zip("n8n/bin/n8n" to "console.log('hi')\n")
        val problem = runCatching { install(payload, expectedSha256 = "00".repeat(32)) }.exceptionOrNull()

        assertTrue(problem is PayloadException)
        assertEquals(PayloadProblem.INTEGRITY_MISMATCH, (problem as PayloadException).problem)
        assertFalse(paths.payloadDir(EmbeddedComponent.N8N, "1.2.3").exists(), "nothing may be activated")
        assertFalse(installer.installed(EmbeddedComponent.N8N) != null)
        assertFalse(
            paths.payloads.listFiles()?.any { it.name.startsWith(".staging") } == true,
            "staging directory must be cleaned up",
        )
    }

    @Test
    fun `refuses entries that escape the payload directory`() {
        val payload = zip("../escape.txt" to "nope", "n8n/bin/n8n" to "x")

        val problem = runCatching { install(payload) }.exceptionOrNull()
        assertTrue(problem is PayloadException, "zip-slip must be rejected: $problem")
        assertEquals(PayloadProblem.UNSAFE_ENTRY, (problem as PayloadException).problem)
        assertFalse(File(root, "escape.txt").exists())
        assertFalse(File(paths.root.parentFile, "escape.txt").exists())
    }

    @Test
    fun `rejects a payload missing a declared entry`() {
        val payload = zip("n8n/package.json" to "{}")
        val problem = runCatching { install(payload) }.exceptionOrNull()

        assertTrue(problem is PayloadException)
        assertEquals(PayloadProblem.ENTRY_MISSING, (problem as PayloadException).problem)
    }

    @Test
    fun `replaces an existing install atomically and keeps the previous version`() {
        install(zip("n8n/bin/n8n" to "v1"), version = "1.0.0")
        install(zip("n8n/bin/n8n" to "v2"), version = "2.0.0")

        val active = installer.installed(EmbeddedComponent.N8N)
        assertEquals("2.0.0", active?.version)
        assertEquals("2.0.0", paths.currentPointer(EmbeddedComponent.N8N).readText())
        assertTrue(paths.payloadDir(EmbeddedComponent.N8N, "1.0.0").isDirectory, "previous version kept for rollback")
    }

    @Test
    fun `prunes old versions to bound storage`() {
        listOf("1.0.0", "2.0.0", "3.0.0", "4.0.0").forEach { version ->
            install(zip("n8n/bin/n8n" to version), version = version)
        }

        val kept = installer.versions(EmbeddedComponent.N8N).sorted()
        assertEquals(listOf("3.0.0", "4.0.0"), kept)
    }

    @Test
    fun `delete clears the pointer so the runtime reports not installed`() {
        install(zip("n8n/bin/n8n" to "x"))
        assertTrue(installer.delete(EmbeddedComponent.N8N, "1.2.3"))

        assertFalse(paths.currentPointer(EmbeddedComponent.N8N).exists())
        assertEquals(null, installer.installed(EmbeddedComponent.N8N))
    }
}
