package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.runtime.ComponentSpec
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedRuntime
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.PayloadException
import com.n8n.mobile.studio.runtime.PayloadProblem
import com.n8n.mobile.studio.runtime.PreparedRuntime
import com.n8n.mobile.studio.runtime.RuntimeInstaller
import com.n8n.mobile.studio.runtime.RuntimeManifest
import com.n8n.mobile.studio.runtime.RuntimePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The embedded n8n runtime.
 *
 * Scope: full local n8n, GUI only (its own editor served on loopback, opened in
 * the in-app WebView or any browser on the device), no tunnel and no external
 * n8n server. Everything n8n writes goes to [N8nStorage] inside the app sandbox.
 */
class N8nRuntime(
    private val paths: RuntimePaths,
    private val installer: RuntimeInstaller,
    private val node: NodeRuntime,
    private val manifest: RuntimeManifest,
    private val config: N8nConfig = N8nConfig(),
) : EmbeddedRuntime {

    override val component: EmbeddedComponent = EmbeddedComponent.N8N

    private val storage = N8nStorage(paths, config)
    private val process = N8nProcess(node, paths, config)

    override fun spec(): ComponentSpec = manifest.spec(component)

    override suspend fun prepare(): Result<PreparedRuntime> = withContext(Dispatchers.IO) {
        runCatching {
            val spec = spec()
            if (!spec.packaged) {
                throw PayloadException(
                    PayloadProblem.NOT_PACKAGED,
                    "n8n ${spec.version.ifBlank { N8nVersion.PINNED }} payload is not packaged in this APK build",
                )
            }
            node.missingReason()?.let { throw PayloadException(PayloadProblem.MISSING_FROM_ASSETS, it) }
            storage.ensure().getOrThrow()

            val installed = installer.installed(component)
                ?: throw PayloadException(
                    PayloadProblem.MISSING_FROM_ASSETS,
                    "n8n payload is not installed on this device",
                )
            val entryRelative = spec.entryHint.ifBlank { N8nVersion.ENTRY }
            val entry = paths.resolveInside(installed.dir, entryRelative)
            if (!entry.isFile) {
                throw PayloadException(
                    PayloadProblem.ENTRY_MISSING,
                    "n8n entry '$entryRelative' is missing from payload ${installed.version}",
                )
            }

            val launch = process.spec(
                installed = installed,
                entryRelative = entryRelative,
                args = spec.args,
                heapMb = config.memoryMb,
            )
            PreparedRuntime(
                component = component,
                launch = launch,
                endpoint = config.endpoint,
                version = installed.version,
                payloadDir = installed.dir,
            )
        }
    }

    fun storage(): N8nStorage = storage
}
