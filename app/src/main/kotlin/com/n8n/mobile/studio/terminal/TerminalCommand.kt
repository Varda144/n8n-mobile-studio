package com.n8n.mobile.studio.terminal
import java.io.File
data class TerminalCommand(val command:String, val args:List<String> = emptyList(), val environment: TerminalEnvironment=TerminalEnvironment()){
    fun toPb():ProcessBuilder{
        val pb = ProcessBuilder(listOf(command)+args)
        pb.directory(File(environment.workingDirectory.takeIf{it.isNotEmpty()} ?: "/"))
        pb.environment().putAll(environment.variables)
        pb.redirectErrorStream(true)
        return pb
    }
    companion object{ fun of(vararg p:String)=TerminalCommand(p.firstOrNull()?:"", p.drop(1)) }
}
