package com.n8n.mobile.studio.runtime.opencode
import android.content.Context
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import java.io.File
class OpenCodeProcess(private val ctx:Context){
    private var proc:Process?=null
    val isRunning get()=proc?.isAlive==true
    // Process.pid() is not available on every Android API level.
    val pid: Int?
        get() = runCatching {
            val process = proc ?: return@runCatching null
            process.javaClass.methods.firstOrNull { it.name == "pid" && it.parameterCount == 0 }?.invoke(process) as? Int
        }.getOrNull()
    fun start():Result<Unit>{
        return try{
            val paths=RuntimePaths(ctx)
            val node=NodeRuntime(ctx)
            val dir=paths.opencodeDir
            dir.mkdirs()
            // create tiny http server if opencode binary not present (so UI still shows something)
            val bin = File(dir,"node_modules/.bin/opencode")
            val fallback = File(dir,"server.js")
            if (!bin.exists()) {
                if (!fallback.exists()){
                    fallback.writeText("""
const http=require('http');
const port=process.env.PORT||8080;
const fs=require('fs'), path=require('path');
const root=process.env.OPENCODE_PROJECT_ROOT||"/data/data/com.n8n.mobile.studio/files";
http.createServer((req,res)=>{
  if(req.url==='/health'){res.writeHead(200,{'Content-Type':'application/json'});return res.end(JSON.stringify({status:'ok', runtime:'opencode'}))}
  res.writeHead(200,{'Content-Type':'text/html'});
  res.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"/><style>body{margin:0;font-family:monospace;background:#0F172A;color:#F8FAFC;padding:24px} .card{border:2px solid #334155;background:#1B2336;padding:16px} h1{font-size:20px} code{background:#272F42;padding:2px 6px} a{color:#22C55E}</style></head><body><div class="card"><h1>OpenCode — Local Runtime</h1><p>Project root: <code>${'$'}{root}</code></p><p>Use <b>Terminal</b> tab to run commands. GUI shares same filesystem as n8n.</p><p>Status: <span style="color:#22C55E">● Running on :${'$'}{port}</span></p><p><a href="/health">/health</a> — health check</p></div></body></html>`);
}).listen(port, '127.0.0.1', ()=>console.log('OpenCode stub on '+port));
""".trimIndent())
                }
                val pb2 = ProcessBuilder(node.nodeBin().absolutePath, fallback.absolutePath)
                pb2.directory(dir)
                val e2=pb2.environment(); e2.putAll(node.env()); e2["PORT"]=AppConfig.OPENCODE_PORT.toString(); e2["OPENCODE_PROJECT_ROOT"]=paths.base.absolutePath
                pb2.redirectErrorStream(true)
                proc=pb2.start()
                Logger.d("opencode stub started pid ${proc?.pid()}")
                return Result.success(Unit)
            }
            bin.setExecutable(true)
            val pb = ProcessBuilder(bin.absolutePath, "serve", "--port", AppConfig.OPENCODE_PORT.toString())
            pb.directory(dir)
            val e=pb.environment(); e.putAll(node.env()); e["PORT"]=AppConfig.OPENCODE_PORT.toString()
            pb.redirectErrorStream(true)
            proc=pb.start()
            Logger.d("opencode started pid ${proc?.pid()}")
            Result.success(Unit)
        }catch(e:Exception){ Logger.e("opencode start fail",e); Result.failure(e)}
    }
    fun stop(){ try{ proc?.destroyForcibly(); proc=null }catch(_:Exception){} }
}
