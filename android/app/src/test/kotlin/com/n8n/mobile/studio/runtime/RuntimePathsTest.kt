package com.n8n.mobile.studio.runtime

import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

/**
 * The runtime tree is the app's sandbox: everything n8n and OpenCode can reach
 * lives under it, and every path that comes from a payload archive passes through
 * [RuntimePaths.resolveInside]. These tests pin that down, because a mistake here
 * is the difference between a contained runtime and one writing into shared
 * storage.
 */
class RuntimePathsTest {

    private val root: File = Files.createTempDirectory("runtime-paths").toFile()
    private val paths = RuntimePaths.forFilesDir(root)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `ensure creates the whole tree including payload roots`() {
        paths.ensure().getOrThrow()

        listOf(paths.root, paths.bin, paths.payloads, paths.home, paths.projects, paths.data, paths.logs, paths.run, paths.tmp)
            .forEach { assertTrue(it.isDirectory, "$it should exist") }
        EmbeddedComponent.entries.forEach { component ->
            assertTrue(paths.componentData(component).isDirectory)
            assertTrue(paths.componentPayloadRoot(component).isDirectory, "payload root must exist before installs")
            assertTrue(paths.componentHome(component).isDirectory)
            assertTrue(paths.componentTmp(component).isDirectory)
        }
    }

    @Test
    fun `everything the runtimes can touch lives under the app-owned root`() {
        paths.ensure().getOrThrow()
        val members = listOf(
            paths.home.absolutePath,
            paths.projects.absolutePath,
            paths.componentData(EmbeddedComponent.N8N).absolutePath,
            paths.logFile(EmbeddedComponent.N8N).absolutePath,
            paths.pidFile(EmbeddedComponent.OPENCODE).absolutePath,
            paths.payloadDir(EmbeddedComponent.N8N, "2.38.7").absolutePath,
        )
        members.forEach { member ->
            assertTrue(member.startsWith(paths.root.absolutePath), "$member escaped ${paths.root}")
        }
    }

    @Test
    fun `resolveInside accepts nested paths and rejects escapes`() {
        val base = File(paths.payloads, "n8n/1.0.0").apply { mkdirs() }
        assertEquals(
            File(base, "n8n/bin/n8n").canonicalPath,
            paths.resolveInside(base, "n8n/bin/n8n").canonicalPath,
        )
        // Absolute-looking entries are treated as archive-relative, not absolute.
        assertEquals(
            File(base, "n8n/bin/n8n").canonicalPath,
            paths.resolveInside(base, "/n8n/bin/n8n").canonicalPath,
        )

        listOf("../escape", "n8n/../../escape", "..", "n8n/../../../etc/passwd").forEach { hostile ->
            val problem = runCatching { paths.resolveInside(base, hostile) }.exceptionOrNull()
            assertTrue(problem is IllegalArgumentException, "'$hostile' must be rejected, got $problem")
        }
        assertEquals(
            0,
            File(root, "escape").let { if (it.exists()) 1 else 0 },
            "nothing may be created outside the payload root",
        )
    }

    @Test
    fun `version strings are sanitised before they become directory names`() {
        val hostile = paths.payloadDir(EmbeddedComponent.N8N, "../../etc")
        val rootPath = paths.componentPayloadRoot(EmbeddedComponent.N8N).canonicalPath
        assertTrue(
            hostile.canonicalPath.startsWith(rootPath + File.separator),
            "sanitised version must stay under the component payload root, was ${hostile.canonicalPath}",
        )
        assertFalse(hostile.name.contains('/'), "a version can never introduce a path separator")
        assertEquals("unversioned", paths.payloadDir(EmbeddedComponent.N8N, "").name)
    }

    @Test
    fun `per component paths do not collide`() {
        assertEquals("n8n.log", paths.logFile(EmbeddedComponent.N8N).name)
        assertEquals("opencode.pid", paths.pidFile(EmbeddedComponent.OPENCODE).name)
        assertTrue(paths.componentData(EmbeddedComponent.N8N) != paths.componentData(EmbeddedComponent.OPENCODE))
        assertTrue(paths.componentHome(EmbeddedComponent.N8N) != paths.componentHome(EmbeddedComponent.OPENCODE))
    }

    @Test
    fun `version ordering prefers the newest release not the newest string`() {
        val sorted = Versions.newestFirst(listOf("1.9.0", "1.10.0", "2.0.0-rc.1", "2.0.0"))
        assertEquals(listOf("2.0.0", "2.0.0-rc.1", "1.10.0", "1.9.0"), sorted)
        assertTrue(Versions.compare("1.10.0", "1.9.0") > 0)
        assertTrue(Versions.compare("v2.38.7", "2.38.7") == 0)
        assertTrue(Versions.compare("2.38.7", "2.38.10") < 0)
    }
}
