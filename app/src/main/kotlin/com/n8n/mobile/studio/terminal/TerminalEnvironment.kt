package com.n8n.mobile.studio.terminal
data class TerminalEnvironment(val workingDirectory:String="/", val variables:Map<String,String> = emptyMap(), val shell:String="/system/bin/sh"){
    fun toArray():Array<String> = variables.map{ "${it.key}=${it.value}"}.toTypedArray()
}
