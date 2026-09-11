package com.n8n.mobile.studio.runtime.android

import android.content.Context
import android.net.Uri
import com.n8n.mobile.studio.runtime.ComponentSpec
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.InstallProgress
import com.n8n.mobile.studio.runtime.InstallRequest
import com.n8n.mobile.studio.runtime.InstalledPayload
import com.n8n.mobile.studio.runtime.PayloadException
import com.n8n.mobile.studio.runtime.PayloadProblem
import com.n8n.mobile.studio.runtime.RuntimeInstaller
import com.n8n.mobile.studio.runtime.RuntimeManifest
import com.n8n.mobile.studio.runtime.RuntimePins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges "what the APK/imports contain" with "what is installed in the sandbox".
 *
 * The app never invents a runtime: [status] reports `NOT_INSTALLED` with a
 * concrete reason (payload not packaged, ABI not covered, digest mismatch, entry
 * missing) and the UI renders exactly that.
 */
class PayloadManager(
    private val context: Context,
    private val env: AndroidRuntimeEnv,
    private val installer: RuntimeInstaller = env.installer(),
) {

    @Volatile
    private var cachedManifest: RuntimeManifest? = null

    fun manifest(): RuntimeManifest = cachedManifest ?: env.manifest().also { cachedManifest = it }

    fun invalidate() {
        cachedManifest = null
    }

    data class PayloadStatus(
        val component: EmbeddedComponent,
        val spec: ComponentSpec,
        val installed: InstalledPayload?,
        val assetAvailable: Boolean,
        val problem: PayloadProblem?,
        val message: String,
    ) {
        val ready: Boolean get() = installed?.isUsable() == true && problem == null
    }

    fun status(component: EmbeddedComponent): PayloadStatus {
        val manifest = manifest()
        val spec = manifest.spec(component)
        val installed = installer.installed(component)
        val asset = AssetPayloadSource.create(context, component.id, RuntimePins.PAYLOAD_ARCHIVE)

        val problem = when {
            !spec.enabled -> PayloadProblem.NOT_PACKAGED
            !spec.packaged -> PayloadProblem.NOT_PACKAGED
            installed == null -> PayloadProblem.MISSING_FROM_ASSETS
            else -> null
        }
        val message = when (problem) {
            PayloadProblem.NOT_PACKAGED ->
                "${component.label} payload is not packaged in this APK build " +
                    "(manifest says packaged=false)"
            PayloadProblem.MISSING_FROM_ASSETS ->
                if (asset != null) {
                    "${component.label} payload is bundled but not installed yet"
                } else {
                    "${component.label} payload is not installed and this APK ships no archive for it"
                }
            null -> "Payload ${installed?.version} installed"
            else -> "Payload problem: $problem"
        }
        return PayloadStatus(component, spec, installed, assetAvailable = asset != null, problem = problem, message = message)
    }

    fun installRequest(component: EmbeddedComponent, versionOverride: String? = null): InstallRequest {
        val spec = manifest().spec(component)
        val entry = manifest().entry(component)
        return InstallRequest(
            component = component,
            version = versionOverride ?: spec.version.ifBlank { "unknown" },
            expectedSha256 = entry?.payload?.sha256.orEmpty(),
            abi = env.abis.firstOrNull { RuntimePins.SUPPORTED_ABIS.contains(it) }.orEmpty(),
            requiredEntries = listOfNotNull(entry?.entry?.takeIf { it.isNotBlank() }),
        )
    }

    /** Install the archive bundled in the APK. */
    suspend fun installFromAssets(
        component: EmbeddedComponent,
        onProgress: (InstallProgress) -> Unit = {},
    ): Result<InstalledPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val source = AssetPayloadSource.create(context, component.id, RuntimePins.PAYLOAD_ARCHIVE)
                ?: throw PayloadException(
                    PayloadProblem.MISSING_FROM_ASSETS,
                    "this APK ships no assets/runtime/${component.id}/${RuntimePins.PAYLOAD_ARCHIVE}",
                )
            val installed = installer.install(source, installRequest(component), onProgress)
            installed
        }
    }

    /** Install a payload picked from device storage (no rebuild, no reinstall). */
    suspend fun installFromUri(
        component: EmbeddedComponent,
        uri: Uri,
        onProgress: (InstallProgress) -> Unit = {},
    ): Result<InstalledPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val source = UriPayloadSource(context.contentResolver, uri)
            val request = installRequest(component).copy(
                // Imported archives are built by CI, which reports the digest in
                // the manifest; when it does not match we refuse the install.
                expectedSha256 = manifest().entry(component)?.payload?.sha256.orEmpty(),
            )
            installer.install(source, request, onProgress)
        }
    }

    fun uninstall(component: EmbeddedComponent): Result<Unit> = runCatching {
        installer.versions(component).forEach { installer.delete(component, it) }
    }

    fun installedVersion(component: EmbeddedComponent): InstalledPayload? = installer.installed(component)

    fun versions(component: EmbeddedComponent): List<String> = installer.versions(component)
}
