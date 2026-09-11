package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.runtime.n8n.N8nConfig
import com.n8n.mobile.studio.runtime.n8n.N8nRuntime
import com.n8n.mobile.studio.runtime.opencode.OpenCodeConfig
import com.n8n.mobile.studio.runtime.opencode.OpenCodeRuntime

/**
 * Builds the two embedded runtimes from the packaged manifest and the current
 * user configuration.
 *
 * The manager deliberately does *not* own processes: [RuntimeSupervisor] does.
 * Runtimes are created on demand so that a configuration change (port, memory
 * budget, freshly rotated n8n encryption key, new provider credential) is picked
 * up by the next launch instead of being frozen at service creation.
 */
class LocalRuntimeManager(
    private val paths: RuntimePaths,
    private val installer: RuntimeInstaller,
    private val node: NodeRuntime,
    private val manifest: () -> RuntimeManifest,
    private val n8nConfig: () -> N8nConfig,
    private val openCodeConfig: () -> OpenCodeConfig,
) {

    fun runtime(component: EmbeddedComponent): EmbeddedRuntime = when (component) {
        EmbeddedComponent.N8N -> N8nRuntime(
            paths = paths,
            installer = installer,
            node = node,
            manifest = manifest(),
            config = n8nConfig(),
        )

        EmbeddedComponent.OPENCODE -> OpenCodeRuntime(
            paths = paths,
            installer = installer,
            node = node,
            manifest = manifest(),
            config = openCodeConfig(),
        )
    }

    fun specs(): Map<EmbeddedComponent, ComponentSpec> = EmbeddedComponent.entries.associateWith { component ->
        manifest().spec(component)
    }

    /** Expected payload location for a component, used for orphan detection. */
    fun payloadMarker(component: EmbeddedComponent): String = "/payloads/${component.id}/"
}
