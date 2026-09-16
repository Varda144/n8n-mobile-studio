package com.n8n.mobile.studio.runtime.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.core.Logger
import kotlinx.coroutines.*

class LocalRuntimeService: Service(){
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private lateinit var controller: RuntimeServiceController
    private lateinit var notif: RuntimeNotification

    override fun onCreate(){ super.onCreate(); controller=RuntimeServiceController(this); notif=RuntimeNotification(this)
        startForeground(AppConstants.NOTIF_ID, notif.foreground("n8n Mobile Studio","Ready — tap to manage runtimes"))
        Logger.d("LocalRuntimeService created")
    }

    override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int{
        val a=intent?.action
        Logger.d("Service action $a")
        when(a){
            AppConstants.ACTION_START_N8N -> scope.launch{
                update("Starting n8n..."); controller.startN8n(); updateFg()
            }
            AppConstants.ACTION_STOP_N8N -> scope.launch{ controller.stopN8n(); updateFg(); if(!controller.isN8nRunning() && !controller.isOpenCodeRunning()) stopSelf() }
            AppConstants.ACTION_START_OPENCODE -> scope.launch{ update("Starting OpenCode..."); controller.startOpenCode(); updateFg() }
            AppConstants.ACTION_STOP_OPENCODE -> scope.launch{ controller.stopOpenCode(); updateFg(); if(!controller.isN8nRunning() && !controller.isOpenCodeRunning()) stopSelf() }
            "STOP_ALL" -> scope.launch{ controller.stopN8n(); controller.stopOpenCode(); stopSelf() }
        }
        return START_STICKY
    }
    private fun update(text:String){ val n=notif.foreground("n8n Mobile Studio", text); startForeground(AppConstants.NOTIF_ID, n) }
    private fun updateFg(){
        val n8n=controller.isN8nRunning(); val oc=controller.isOpenCodeRunning()
        val title = when{ n8n && oc -> "n8n + OpenCode running"; n8n -> "n8n running :5678"; oc -> "OpenCode running :8080"; else -> "Runtimes stopped"}
        val txt = buildString{ if(n8n) append("n8n 127.0.0.1:5678"); if(n8n&&oc) append("  •  "); if(oc) append("OpenCode 127.0.0.1:8080"); if(isEmpty()) append("Tap to start") }
        startForeground(AppConstants.NOTIF_ID, notif.foreground(title, txt))
    }
    override fun onBind(intent:Intent?):IBinder? = null
    override fun onDestroy(){ super.onDestroy(); scope.cancel(); Logger.d("Service destroyed")}
    fun getController(): RuntimeServiceController = controller
}
