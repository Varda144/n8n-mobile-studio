package com.n8n.mobile.studio.core
import android.util.Log
object Logger {
    private var debug=true
    private const val TAG="N8N_STUDIO"
    fun init(d:Boolean){debug=d}
    fun d(m:String,t:String=TAG){ if(debug) Log.d(t,m) }
    fun e(m:String,e:Throwable?=null,t:String=TAG){ if(e!=null) Log.e(t,m,e) else Log.e(t,m) }
}
