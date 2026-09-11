package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.RuntimePaths
import java.io.File

/**
 * n8n's slice of the app-owned filesystem.
 *
 * Everything n8n persists — SQLite database, encryption key file, binary data,
 * logs — stays inside the component directory, so "wipe local n8n data" is a
 * single bounded delete and no workflow ever escapes the sandbox.
 */
class N8nStorage(
    private val paths: RuntimePaths,
    private val config: N8nConfig = N8nConfig(),
) {

    private val component = EmbeddedComponent.N8N

    /** `N8N_USER_FOLDER`; n8n creates `.n8n/` inside it. */
    fun userFolder(): File = paths.componentData(component)

    fun n8nFolder(): File = File(userFolder(), ".n8n")

    fun databaseFile(): File = File(n8nFolder(), "database.sqlite")

    fun binaryDataDir(): File = File(n8nFolder(), "binaryData")

    fun logsDir(): File = File(n8nFolder(), "logs")

    fun backupDir(): File = File(userFolder(), "backups")

    fun ensure(): Result<Unit> = runCatching {
        paths.ensure().getOrThrow()
        listOf(userFolder(), n8nFolder(), binaryDataDir(), logsDir(), backupDir()).forEach { dir ->
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                throw IllegalStateException("Cannot create ${dir.absolutePath}")
            }
        }
    }

    fun isInitialised(): Boolean = databaseFile().isFile

    /** Bytes used by n8n's data, shown in Settings. */
    fun usedBytes(): Long = userFolder().walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /**
     * Delete n8n's own data. Only ever called from an explicit, confirmed user
     * action — the payload itself is managed separately by the installer.
     */
    fun wipe(): Result<Unit> = runCatching {
        check(userFolder().deleteRecursively()) { "Cannot delete ${userFolder().absolutePath}" }
        ensure().getOrThrow()
    }
}
