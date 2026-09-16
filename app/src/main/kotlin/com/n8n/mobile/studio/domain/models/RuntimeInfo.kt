package com.n8n.mobile.studio.domain.models
import com.n8n.mobile.studio.runtime.RuntimeState
import com.n8n.mobile.studio.runtime.RuntimeInstaller
data class RuntimeInfo(val type:RuntimeInstaller.RuntimeType,val name:String,val version:String,val state:RuntimeState,val port:Int,val url:String,val installed:Boolean)
