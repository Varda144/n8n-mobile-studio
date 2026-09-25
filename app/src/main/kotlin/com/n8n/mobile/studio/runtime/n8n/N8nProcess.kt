package com.n8n.mobile.studio.runtime.n8n
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import android.content.Context
import java.io.File
class N8nProcess(private val ctx:Context){
    private var proc:Process?=null
    val isRunning get()=proc?.isAlive==true
    val pid get()=try{ proc?.pid()?.toInt()}catch(_:Exception){null}
    fun start(extraEnv: Map<String, String> = emptyMap()): Result<Unit> {
        return try {
            val paths = RuntimePaths(ctx)
            val node = NodeRuntime(ctx)
            val n8nDir = paths.n8nDir
            // Invoke the JavaScript entry point through node directly. The npm
            // .bin/n8n wrapper is a POSIX shell script and is not reliable on Android.
            val cli = File(n8nDir, "node_modules/n8n/bin/n8n")
            val nodeBin = node.nodeBin()
            if (!cli.isFile || !nodeBin.isFile) {
                return Result.failure(IllegalStateException("n8n or Node runtime is not installed"))
            }
            val dataDir = paths.n8nData.absolutePath
            File(dataDir).mkdirs()
            val env = mutableMapOf(
                "N8N_PORT" to com.n8n.mobile.studio.core.AppConfig.N8N_PORT.toString(),
                "N8N_USER_FOLDER" to dataDir,
                "N8N_PROTOCOL" to "http",
                "N8N_HOST" to "127.0.0.1",
                "NODE_ENV" to "production",
                "N8N_SECURE_COOKIE" to "false",
                "N8N_DIAGNOSTICS_ENABLED" to "false",
                "N8N_VERSION_NOTIFICATIONS_ENABLED" to "false"
            ) + extraEnv
            val pb = ProcessBuilder(nodeBin.absolutePath, cli.absolutePath, "start")
            pb.directory(n8nDir)
            val e = pb.environment()
            e.putAll(node.env())
            e.putAll(env)
            // Do not leave an unread pipe: n8n can emit enough output to fill it.
            pb.redirectErrorStream(true)
            pb.redirectOutput(File(n8nDir, "n8n.log"))
            proc = pb.start()
            Logger.d("n8n started pid ${proc?.pid()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("N8nProcess start fail", e)
            Result.failure(e)
        }
    }
    fun stop(){ try{ proc?.destroyForcibly(); proc=null }catch(_:Exception){} }
}
