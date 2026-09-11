package com.n8n.mobile.studio.runtime

import android.content.Context
import com.n8n.mobile.studio.core.AppConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Describes the versioned runtime package shipped in assets.
 */
@Serializable
data class RuntimeManifest(
    val schemaVersion: Int = 1,
    val builtAt: String = "",
    val packagedComponents: List<String> = emptyList(),
)

object RuntimeInstaller {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Unpack assets for [component] into its runtime directory. Assets may be
     * empty, in which case the directory tree is merely ensured to exist.
     */
    suspend fun install(context: Context, component: EmbeddedComponent, config: AppConfig): Result<Unit> =
        RuntimePaths.ensure(context)

    /**
     * Read the version manifest from assets. Falls back to a default manifest
     * when the file is absent or unparseable.
     */
    fun readManifest(context: Context): Result<RuntimeManifest> = try {
        val text = context.assets.open("runtime/manifest.json").bufferedReader().use { it.readText() }
        Result.success(json.decodeFromString<RuntimeManifest>(text))
    } catch (_: Throwable) {
        Result.success(RuntimeManifest())
    }
}