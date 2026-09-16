package com.n8n.mobile.studio.runtime.opencode
import com.n8n.mobile.studio.runtime.RuntimeHealth
data class OpenCodeHealth(val running:Boolean,val error:String?=null){ fun toHealth()=RuntimeHealth(running,error=error)}
