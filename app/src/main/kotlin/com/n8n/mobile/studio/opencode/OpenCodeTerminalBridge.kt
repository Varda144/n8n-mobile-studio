package com.n8n.mobile.studio.opencode

import android.content.Context
import com.n8n.mobile.studio.terminal.TerminalCommand
import com.n8n.mobile.studio.terminal.TerminalEngine
import com.n8n.mobile.studio.terminal.TerminalSession

class OpenCodeTerminalBridge(ctx:Context){
    private val engine = TerminalEngine(ctx)
    private var sess: TerminalSession? = null
    fun session(): TerminalSession { if(sess==null) sess=engine.createSession(); return sess!! }
    suspend fun exec(cmd:String):String{
        val s=session()
        val res=engine.execute(s, TerminalCommand.of("sh","-c",cmd))
        return res.output
    }
    fun close(){ sess?.let{ engine.close(it.id)}; sess=null }
}
