package com.n8n.mobile.studio.runtime.n8n
import com.n8n.mobile.studio.runtime.RuntimeHealth
data class N8nHealth(val running:Boolean,val pid:Int?=null,val error:String?=null){ fun toHealth()=RuntimeHealth(running,pid,error=error) }
