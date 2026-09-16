package com.n8n.mobile.studio.runtime.n8n
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.runtime.RuntimePaths
import android.content.Context
data class N8nConfig(val port:Int=AppConfig.N8N_PORT,val dataDir:String,val logDir:String){
    companion object{ fun from(ctx:Context):N8nConfig { val p=RuntimePaths(ctx); return N8nConfig(dataDir=p.n8nData.absolutePath, logDir=p.n8nData.absolutePath)}}
}
