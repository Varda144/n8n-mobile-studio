package com.n8n.mobile.studio.terminal

import android.content.Context
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class TerminalEngine(private val ctx:Context){
    private val sessions = mutableMapOf<String, TerminalSession>()
    fun createSession(env:TerminalEnvironment=TerminalEnvironment()): TerminalSession {
        // inject termux env so node/npm/n8n available in terminal
        val paths = RuntimePaths(ctx)
        val nodeEnv = NodeRuntime(ctx).env()
        val merged = env.copy(variables = nodeEnv + env.variables, workingDirectory = env.workingDirectory.ifEmpty{ paths.base.absolutePath })
        val s = TerminalSession(env = merged)
        sessions[s.id]=s; return s
    }
    fun get(id:String)=sessions[id]
    fun close(id:String){ sessions[id]?.close(); sessions.remove(id) }
    suspend fun execute(session: TerminalSession, cmd: TerminalCommand): TerminalOutput = withContext(Dispatchers.IO){
        try{
            val pb = cmd.toPb()
            val proc = pb.start()
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream)).use{ r ->
                var l:String?
                while(r.readLine().also{l=it}!=null){ sb.appendLine(l); session.add(TerminalOutput.partial(l?:"")) }
            }
            val code = proc.waitFor()
            val res = if(code==0) TerminalOutput.success(sb.toString(),code) else TerminalOutput.error(sb.toString(),code)
            session.add(res); res
        }catch(e:Exception){ Logger.e("term exec fail",e); val er=TerminalOutput.error(e.message?:"error"); session.add(er); er }
    }
}
