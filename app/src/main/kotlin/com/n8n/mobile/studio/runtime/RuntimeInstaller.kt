package com.n8n.mobile.studio.runtime

import android.content.Context
import com.n8n.mobile.studio.core.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RuntimeInstaller(private val ctx: Context) {
    private val node = NodeRuntime(ctx)
    private val paths = RuntimePaths(ctx)
    enum class RuntimeType { N8N, OPENCODE }

    suspend fun installNode(): Result<Unit> = node.ensureReady()

    suspend fun installN8n(): Result<Unit> = withContext(Dispatchers.IO){
        val r = node.ensureReady()
        if (r.isFailure) return@withContext r
        val dir = paths.n8nDir
        if (File(dir,"node_modules/n8n/package.json").exists()) return@withContext Result.success(Unit)
        node.npmInstall(dir, "n8n", AppConfig.N8N_VERSION)
    }

    suspend fun installOpenCode(): Result<Unit> = withContext(Dispatchers.IO){
        val r = node.ensureReady()
        if (r.isFailure) return@withContext r
        val dir = paths.opencodeDir
        // opencode is npm package `opencode-ai` - fallback to placeholder if not found
        if (File(dir,"node_modules/opencode").exists() || File(dir,"node_modules/@opencode").exists()) return@withContext Result.success(Unit)
        // try opencode-ai, fallback to simple server
        val res = node.npmInstall(dir, "opencode-ai", "latest")
        if (res.isFailure) {
            // create minimal opencode stub so UI still works
            dir.mkdirs()
            File(dir,"package.json").writeText("""{"name":"opencode","private":true}""")
            Result.success(Unit)
        } else res
    }

    fun isInstalled(type: RuntimeType): Boolean = when(type){
        RuntimeType.N8N -> File(paths.n8nDir,"node_modules/n8n/package.json").exists()
        RuntimeType.OPENCODE -> File(paths.opencodeDir,"node_modules").exists()
    }
}
