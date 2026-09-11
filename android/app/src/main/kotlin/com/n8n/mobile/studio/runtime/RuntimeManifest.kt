package com.n8n.mobile.studio.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * The packaged runtime manifest (`assets/runtime/manifest.json`).
 *
 * It is the contract between the payload build (`scripts/prepare-runtime.sh`,
 * `.github/workflows/runtime-build.yml`) and the app: versions, digests,
 * endpoints and entry points. Nothing in this app may claim a runtime is
 * embedded unless the manifest says `packaged = true` *and* the payload with the
 * matching digest is installed.
 */
@Serializable
data class RuntimeManifest(
    val schemaVersion: Int = 0,
    val manifestVersion: String = "",
    val builtAt: String = "",
    val node: NodePayload? = null,
    val runtimes: Map<String, RuntimeEntry> = emptyMap(),
    /** Human-readable notes about payload provenance shown in Settings. */
    val notes: List<String> = emptyList(),
) {

    fun entry(component: EmbeddedComponent): RuntimeEntry? = runtimes[component.id]

    fun spec(component: EmbeddedComponent): ComponentSpec {
        val entry = entry(component)
        return ComponentSpec(
            component = component,
            enabled = entry?.enabled ?: false,
            packaged = entry?.packaged ?: false,
            version = entry?.version ?: "",
            port = entry?.port ?: 0,
            healthPath = entry?.healthPath ?: "",
            guiPath = entry?.guiPath,
            memoryMb = entry?.memoryMb ?: RuntimePins.DEFAULT_NODE_HEAP_MB,
            entryHint = entry?.entry ?: "",
            integrity = entry?.integrity ?: "",
            args = entry?.args ?: emptyList(),
            environment = entry?.environment ?: emptyMap(),
        )
    }

    /** Node runtime identity for the ABI the device actually runs. */
    fun nodeFor(abi: String): AbiPayload? = node?.abis?.get(abi)

    companion object {
        /** JSON decoder tolerant to forward-compatible manifest additions. */
        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = false
            explicitNulls = false
        }

        /**
         * Parse a manifest, rejecting values the app cannot honour. Produces a
         * list of human-readable problems instead of throwing so Settings can
         * show exactly what is wrong with a payload build.
         */
        fun parse(text: String): Result<RuntimeManifest> = runCatching {
            val parsed = json.decodeFromString<RuntimeManifest>(text)
            val problems = validate(parsed)
            check(problems.isEmpty()) { problems.joinToString("; ") }
            parsed
        }

        fun validate(manifest: RuntimeManifest): List<String> = buildList {
            if (manifest.schemaVersion != RuntimePins.SCHEMA_VERSION) {
                add(
                    "manifest schemaVersion=${manifest.schemaVersion} is not supported " +
                        "(expected ${RuntimePins.SCHEMA_VERSION})",
                )
            }
            manifest.runtimes.forEach { (id, entry) ->
                val component = EmbeddedComponent.fromId(id)
                if (component == null) {
                    add("unknown runtime '$id'")
                    return@forEach
                }
                if (!entry.enabled) return@forEach
                if (entry.port <= 0 || entry.port > 65535) {
                    add("${component.id}: invalid port ${entry.port}")
                }
                if (entry.healthPath.isBlank() || !entry.healthPath.startsWith("/")) {
                    add("${component.id}: healthPath must start with '/'")
                }
                if (entry.packaged) {
                    if (entry.version.isBlank()) add("${component.id}: packaged without a version")
                    if (entry.entry.isBlank()) add("${component.id}: packaged without an entry point")
                    val digest = entry.payload?.sha256.orEmpty()
                    if (digest.length != 64) {
                        add("${component.id}: packaged payload needs a sha256 digest")
                    }
                }
            }
            manifest.node?.let { node ->
                if (!node.packaged) return@let
                if (node.abis.isEmpty()) {
                    add("node: packaged without any ABI digests")
                }
                node.abis.forEach { (abi, payload) ->
                    if (payload.sha256.length != 64) add("node/$abi: invalid sha256 digest")
                }
            }
        }
    }
}

@Serializable
data class NodePayload(
    val version: String = "",
    val distOs: String = "",
    val shared: Boolean = false,
    val library: String = RuntimePins.NODE_CORE_LIB,
    val flavor: String = RuntimePins.NODE_BUILD_FLAVOR,
    val extraLibs: List<String> = RuntimePins.NODE_EXTRA_LIBS,
    val source: String = "",
    val packaged: Boolean = false,
    val abis: Map<String, AbiPayload> = emptyMap(),
)

@Serializable
data class AbiPayload(
    val sha256: String = "",
    val sizeBytes: Long = 0,
)

@Serializable
data class RuntimeEntry(
    val enabled: Boolean = false,
    val packaged: Boolean = false,
    val version: String = "",
    /** Entry script relative to the installed payload root. */
    val entry: String = "",
    val args: List<String> = emptyList(),
    val port: Int = 0,
    val healthPath: String = "",
    /** Path served by the runtime for its own web GUI, when it has one. */
    val guiPath: String? = null,
    /** V8 heap budget for this component on a phone. */
    val memoryMb: Int = RuntimePins.DEFAULT_NODE_HEAP_MB,
    /** Upstream provenance (npm `dist.integrity`, git commit, …). */
    val integrity: String = "",
    val source: String = "",
    val license: String = "",
    val payload: PayloadRef? = null,
    /** Extra environment added to the process (never secrets). */
    @SerialName("env") val environment: Map<String, String> = emptyMap(),
)

@Serializable
data class PayloadRef(
    /** `asset` (bundled in the APK) or `file` (imported on device). */
    val kind: String = "asset",
    val path: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0,
)
