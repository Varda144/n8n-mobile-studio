package com.n8n.mobile.studio.runtime
import java.io.File
data class RuntimeProcess(val pid:Int,val process:Process,val cmd:List<String>,val wd:File){
    val isAlive get()=process.isAlive
    fun destroy(){ try{ process.destroyForcibly() }catch(_:Exception){} }
}
