package com.n8n.mobile.studio.runtime

/**
 * The two — and only two — local software runtimes this app embeds.
 *
 * Both run as separate OS processes owned by [com.n8n.mobile.studio.runtime.service.LocalRuntimeService];
 * the Compose UI (GUI) and the terminal front-end talk to the same processes.
 */
enum class EmbeddedComponent(
    val id: String,
    val label: String,
    /** Directory name for runtime data under the app-owned runtime root. */
    val dataDir: String,
    /** Lower is more important; used when memory pressure forces a sacrifice. */
    val priority: Int,
) {
    N8N(id = "n8n", label = "n8n", dataDir = "n8n-home", priority = 0),
    OPENCODE(id = "opencode", label = "OpenCode", dataDir = "opencode-home", priority = 1),
    ;

    companion object {
        fun fromId(id: String?): EmbeddedComponent? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Lifecycle state of an embedded component.
 *
 * [NOT_INSTALLED] exists so the UI can never claim a runtime is embedded
 * before its payload has actually been packaged *and* launched on Android.
 */
enum class EmbeddedProcessState {
    NOT_INSTALLED,
    STOPPED,
    STARTING,
    RUNNING,
    DEGRADED,
    STOPPING,
    FAILED,
    ;

    val isActive: Boolean get() = this == STARTING || this == RUNNING || this == DEGRADED
}

/**
 * Snapshot of one component's runtime status, shared between the service, the
 * GUI and the terminal.
 */
data class EmbeddedComponentStatus(
    val component: EmbeddedComponent,
    val state: EmbeddedProcessState = EmbeddedProcessState.NOT_INSTALLED,
    val pid: Long? = null,
    val endpoint: String? = null,
    val version: String? = null,
    val message: String = "Runtime payload not installed",
    val healthDetail: String? = null,
    val restartCount: Int = 0,
    val startedAt: Long? = null,
    val payloadReady: Boolean = false,
)

/**
 * Static, manifest-derived description of a component: where it listens and how
 * to launch it. Populated from [RuntimeManifest], never invented at runtime.
 */
data class ComponentSpec(
    val component: EmbeddedComponent,
    val enabled: Boolean = false,
    val packaged: Boolean = false,
    val version: String = "",
    val port: Int = 0,
    val healthPath: String = "",
    val guiPath: String? = null,
    val memoryMb: Int = RuntimePins.DEFAULT_NODE_HEAP_MB,
    val entryHint: String = "",
    val integrity: String = "",
    /** CLI arguments for the entry script, from the packaged manifest. */
    val args: List<String> = emptyList(),
    /** Extra process environment from the packaged manifest (never secrets). */
    val environment: Map<String, String> = emptyMap(),
)

/**
 * Contract implemented by n8n and OpenCode.
 *
 * A runtime is responsible for preparing its own storage and describing how to
 * launch itself. Process spawning, supervision, health gating, restarts and
 * teardown are handled once, in [RuntimeSupervisor], so both runtimes share an
 * identical lifecycle on low-memory devices.
 */
interface EmbeddedRuntime {
    val component: EmbeddedComponent

    /**
     * Static description resolved from the packaged manifest. Runtime status is
     * *not* here on purpose: the single owner of lifecycle state is
     * [RuntimeSupervisor], so the GUI and the terminal can never disagree.
     */
    fun spec(): ComponentSpec

    /**
     * Ensure the component's storage exists and resolve where its payload is
     * installed. Fails when no payload is packaged, which is reported to the
     * user as [EmbeddedProcessState.NOT_INSTALLED] instead of a fake runtime.
     */
    suspend fun prepare(): Result<PreparedRuntime>
}

/**
 * Everything the supervisor needs to spawn a runtime process.
 */
data class PreparedRuntime(
    val component: EmbeddedComponent,
    val launch: LaunchSpec,
    val endpoint: String,
    val version: String,
    val payloadDir: java.io.File,
)
