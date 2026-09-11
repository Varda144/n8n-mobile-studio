package com.n8n.mobile.studio.runtime.opencode

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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The embedded OpenCode runtime: a local coding agent with a GUI surface, a
 * terminal surface and access to the app-owned project tree.
 *
 * If the payload build could not produce a Bionic-runnable bundle, [prepare]
 * fails with [PayloadProblem.NOT_PACKAGED] and the UI says so. That is the
 * deliberate alternative to quietly pointing the "GUI" at a remote service.
 */
class OpenCodeRuntime(
    private val paths: RuntimePaths,
    private val installer: RuntimeInstaller,
    private val node: NodeRuntime,
    private val manifest: RuntimeManifest,
    private val config: OpenCodeConfig = OpenCodeConfig(),
) : EmbeddedRuntime {

    override val component: EmbeddedComponent = EmbeddedComponent.OPENCODE

    private val process = OpenCodeProcess(node, paths, config)

    override fun spec(): ComponentSpec = manifest.spec(component)

    /** Projects root handed to the agent (created on demand). */
    fun projectsRoot(): File = File(paths.projects, config.projectsDir)

    override suspend fun prepare(): Result<PreparedRuntime> = withContext(Dispatchers.IO) {
        runCatching {
            val spec = spec()
            if (!spec.packaged) {
                throw PayloadException(
                    PayloadProblem.NOT_PACKAGED,
                    "OpenCode ${spec.version.ifBlank { OpenCodeVersion.PINNED }} payload is not packaged " +
                        "in this APK build",
                )
            }
            node.missingReason()?.let { throw PayloadException(PayloadProblem.MISSING_FROM_ASSETS, it) }
            paths.ensure().getOrThrow()
            projectsRoot().mkdirs()

            val installed = installer.installed(component)
                ?: throw PayloadException(
                    PayloadProblem.MISSING_FROM_ASSETS,
                    "OpenCode payload is not installed on this device",
                )
            val entryRelative = spec.entryHint.ifBlank { OpenCodeVersion.ENTRY }
            val entry = paths.resolveInside(installed.dir, entryRelative)
            if (!entry.isFile) {
                throw PayloadException(
                    PayloadProblem.ENTRY_MISSING,
                    "OpenCode entry '$entryRelative' is missing from payload ${installed.version}",
                )
            }

            val args = spec.args.ifEmpty { OpenCodeVersion.SERVE_ARGS }
            val launch = process.spec(
                installed = installed,
                entryRelative = entryRelative,
                args = args,
                heapMb = config.memoryMb,
                extraEnv = spec.environment,
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
}
