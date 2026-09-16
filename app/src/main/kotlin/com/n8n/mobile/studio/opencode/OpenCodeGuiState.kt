package com.n8n.mobile.studio.opencode
import com.n8n.mobile.studio.runtime.RuntimeState
data class OpenCodeGuiState(val state:RuntimeState=RuntimeState.IDLE, val isConnected:Boolean=false, val url:String?=null, val error:String?=null)
