package com.n8n.mobile.studio.runtime.n8n

import android.content.Context
import com.n8n.mobile.studio.runtime.RuntimePaths
import java.io.File

class N8nStorage(
    private val context: Context,
    private val config: N8nConfig = N8nConfig(),
) {
    fun root(): File = File(RuntimePaths.data(context), config.userFolder)

    fun ensure(): Result<Unit> = runCatching {
        listOf(
            root(),
            File(root(), ".n8n"),
            File(root(), "n8n-data"),
            File(root(), "logs"),
        ).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Failed to create ${dir.absolutePath}")
            }
        }
    }
}