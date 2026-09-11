package com.n8n.mobile.studio.runtime

import android.content.Context
import com.n8n.mobile.studio.core.AppResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves component-specific filesystem locations under the app files dir.
 */
object RuntimePaths {
    fun root(context: Context): File = File(context.filesDir, "local-runtime")

    fun componentRoot(context: Context, component: EmbeddedComponent): File =
        File(root(context), component.name.lowercase())

    fun bin(context: Context): File = File(root(context), "bin")

    fun home(context: Context): File = File(root(context), "home")

    fun projects(context: Context): File = File(root(context), "projects")

    fun logs(context: Context): File = File(root(context), "logs")

    fun data(context: Context): File = File(root(context), "data")

    fun tmp(context: Context): File = File(root(context), "tmp")

    /**
     * Create the shared runtime directory tree plus both component roots.
     */
    suspend fun ensure(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        AppResult.run {
            listOf(
                root(context),
                bin(context),
                home(context),
                projects(context),
                logs(context),
                data(context),
                tmp(context),
                componentRoot(context, EmbeddedComponent.N8N),
                componentRoot(context, EmbeddedComponent.OPENCODE),
            ).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) {
                    throw IllegalStateException("Failed to create runtime dir ${dir.absolutePath}")
                }
            }
        }.toResult()
    }
}