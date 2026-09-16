package com.n8n.mobile.studio.runtime
data class RuntimeHealth(val isRunning:Boolean,val pid:Int?=null,val uptime:Long=0L,val error:String?=null){
    val isHealthy get()=isRunning && error==null
    companion object{
        fun unknown()=RuntimeHealth(false,error="unknown")
        fun running(pid:Int)=RuntimeHealth(true,pid=pid)
        fun error(m:String)=RuntimeHealth(false,error=m)
    }
}
