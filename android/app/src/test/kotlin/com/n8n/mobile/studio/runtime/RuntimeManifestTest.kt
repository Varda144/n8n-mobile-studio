package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.runtime.n8n.N8nVersion
import com.n8n.mobile.studio.runtime.opencode.OpenCodeVersion
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Tests the contract between the payload build and the app.
 *
 * The first group parses the manifest that actually ships in `assets/runtime/`:
 * whatever a payload build produces, the app must be able to read it, and it must
 * never be able to claim a runtime is embedded without a digest to back it up.
 * The second group feeds the parser damaged manifests, because a bad manifest
 * must fail loudly at startup rather than half-work on a user's phone.
 */
class RuntimeManifestTest {

    private val shipped: RuntimeManifest = parseShippedManifest()

    @Test
    fun `shipped manifest parses and uses the schema this app implements`() {
        assertEquals(RuntimePins.SCHEMA_VERSION, shipped.schemaVersion)
        assertTrue(shipped.manifestVersion.isNotBlank(), "manifest must carry a version")
        assertNotNull(shipped.node, "manifest must describe the Node engine")
        assertEquals(setOf("n8n", "opencode"), shipped.runtimes.keys)
    }

    @Test
    fun `shipped manifest pins the versions the app is built against`() {
        assertEquals(RuntimePins.NODE_VERSION, shipped.node?.version)
        assertEquals(RuntimePins.N8N_VERSION, shipped.spec(EmbeddedComponent.N8N).version)
        assertEquals(RuntimePins.OPENCODE_VERSION, shipped.spec(EmbeddedComponent.OPENCODE).version)
        assertEquals(RuntimePins.NODE_CORE_LIB, shipped.node?.library)
        assertEquals(RuntimePins.NODE_EXTRA_LIBS, shipped.node?.extraLibs)
        assertEquals(false, shipped.node?.shared, "the engine ships as a self-contained executable")
    }

    @Test
    fun `endpoints are loopback only and match the documented health paths`() {
        val n8n = shipped.spec(EmbeddedComponent.N8N)
        assertEquals(5678, n8n.port)
        assertEquals("/healthz", n8n.healthPath)
        assertEquals(N8nVersion.ENTRY, n8n.entryHint)

        val openCode = shipped.spec(EmbeddedComponent.OPENCODE)
        assertEquals(8765, openCode.port)
        assertEquals("/global/health", openCode.healthPath)
        assertEquals(OpenCodeVersion.ENTRY, openCode.entryHint)

        shipped.runtimes.values.forEach { entry ->
            assertTrue(entry.port in 1024..65535, "port ${entry.port} is not an unprivileged port")
            assertTrue(entry.healthPath.startsWith("/"), "health paths are absolute")
            assertFalse(
                entry.source.contains("http://") && !entry.source.contains("https://"),
                "runtime provenance must use https",
            )
        }
    }

    @Test
    fun `packaged runtimes carry a digest and a declared entry`() {
        shipped.runtimes.forEach { (id, entry) ->
            if (!entry.packaged) return@forEach
            val digest = entry.payload?.sha256.orEmpty()
            assertEquals(64, digest.length, "$id is packaged without a usable sha256")
            assertTrue(entry.entry.isNotBlank(), "$id is packaged without an entry point")
            assertTrue((entry.payload?.sizeBytes ?: 0) > 0, "$id is packaged without a size")
        }
        shipped.node?.let { node ->
            if (!node.packaged) return@let
            assertTrue(node.abis.isNotEmpty(), "node is packaged without an ABI digest")
            node.abis.forEach { (abi, payload) ->
                assertEquals(64, payload.sha256.length, "node/$abi digest is not a sha256")
                assertTrue(payload.sizeBytes > 0, "node/$abi has no size")
            }
        }
    }

    @Test
    fun `unpackaged runtimes make no digest claims`() {
        shipped.runtimes.forEach { (id, entry) ->
            if (entry.packaged) return@forEach
            assertTrue(
                entry.payload?.sha256.isNullOrBlank(),
                "$id is not packaged, so the manifest must not carry a payload digest",
            )
            assertEquals(0L, entry.payload?.sizeBytes ?: 0L)
        }
    }

    @Test
    fun `malformed manifests are rejected with a reason a user can act on`() {
        val badSchema = RuntimeManifest.parse(minimalManifest("\"schemaVersion\": 99,"))
        assertTrue(badSchema.isFailure)
        assertTrue(badSchema.exceptionOrNull()!!.message!!.contains("schemaVersion"))

        val badPort = RuntimeManifest.parse(minimalManifest("\"schemaVersion\": 2,", port = 0))
        assertTrue(badPort.isFailure)
        assertTrue(badPort.exceptionOrNull()!!.message!!.contains("invalid port"))

        val relativeHealth = RuntimeManifest.parse(minimalManifest("\"schemaVersion\": 2,", healthPath = "healthz"))
        assertTrue(relativeHealth.isFailure)
        assertTrue(relativeHealth.exceptionOrNull()!!.message!!.contains("healthPath"))

        val packagedWithoutDigest = RuntimeManifest.parse(
            minimalManifest("\"schemaVersion\": 2,", packaged = true, digest = ""),
        )
        assertTrue(packagedWithoutDigest.isFailure)
        assertTrue(packagedWithoutDigest.exceptionOrNull()!!.message!!.contains("sha256"))

        val unknownRuntime = RuntimeManifest.parse(
            """
            {"schemaVersion": 2, "manifestVersion": "test", "node": {"packaged": false},
             "runtimes": {"codex": {"enabled": true, "port": 1234, "healthPath": "/"}}}
            """.trimIndent(),
        )
        assertTrue(unknownRuntime.isFailure)
        assertTrue(unknownRuntime.exceptionOrNull()!!.message!!.contains("unknown runtime"))
    }

    @Test
    fun `a node engine without digests is rejected when it claims to be packaged`() {
        val manifest = RuntimeManifest.parse(
            """
            {"schemaVersion": 2, "manifestVersion": "test",
             "node": {"version": "${RuntimePins.NODE_VERSION}", "packaged": true, "abis": {}},
             "runtimes": {}}
            """.trimIndent(),
        )
        assertTrue(manifest.isFailure)
        assertTrue(manifest.exceptionOrNull()!!.message!!.contains("ABI"))
    }

    // ------------------------------------------------------------------ helpers

    private fun minimalManifest(
        schemaPrefix: String,
        port: Int = 5678,
        healthPath: String = "/healthz",
        packaged: Boolean = false,
        digest: String = "",
    ): String =
        """
        {
          $schemaPrefix
          "manifestVersion": "test",
          "node": {"packaged": false},
          "runtimes": {
            "n8n": {
              "enabled": true,
              "packaged": $packaged,
              "version": "0.0.0",
              "entry": "n8n/bin/n8n",
              "port": $port,
              "healthPath": "$healthPath",
              "payload": {"kind": "asset", "path": "runtime/n8n/payload.zip", "sha256": "$digest", "sizeBytes": 0}
            }
          }
        }
        """.trimIndent()

    private fun parseShippedManifest(): RuntimeManifest {
        val file = findRepoSideFile("src/main/assets/runtime/manifest.json")
            ?: error(
                "could not locate assets/runtime/manifest.json from ${File(".").absolutePath} — " +
                    "the packaged manifest is the payload contract and must be present",
            )
        return RuntimeManifest.parse(file.readText()).getOrElse { error ->
            error("shipped manifest does not parse: ${error.message}")
        }
    }

    /** Walk up from the working directory until [relative] is found. */
    private fun findRepoSideFile(relative: String): File? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "android/app/$relative").isFile) return File(dir, "android/app/$relative")
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir.parentFile
        }
        return null
    }
}
