package com.n8n.mobile.studio.terminal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
data class TerminalSession(val id:String=UUID.randomUUID().toString(), val env:TerminalEnvironment=TerminalEnvironment()){
    private val _out = MutableStateFlow<List<TerminalOutput>>(emptyList())
    val outputs:StateFlow<List<TerminalOutput>> = _out.asStateFlow()
    private val _active = MutableStateFlow(true)
    val isActive:StateFlow<Boolean> = _active.asStateFlow()
    fun add(o:TerminalOutput){ _out.value = _out.value + o }
    fun clear(){ _out.value=emptyList() }
    fun close(){ _active.value=false }
}
