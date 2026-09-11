package com.n8n.mobile.studio.runtime.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.LocalRuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = LocalRuntimeManager()
    private val notification = RuntimeNotification(this)

    override fun onCreate() {
        super.onCreate()
        notification.createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppConstants.NOTIFICATION_ID,
                notification.build("Local runtime ready"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(AppConstants.NOTIFICATION_ID, notification.build("Local runtime ready"))
        }
        scope.launch { manager.prepare(this@LocalRuntimeService) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_START_N8N ->
                scope.launch { manager.start(this@LocalRuntimeService, EmbeddedComponent.N8N) }
            AppConstants.ACTION_STOP_N8N ->
                scope.launch { manager.stop(EmbeddedComponent.N8N) }
            AppConstants.ACTION_START_OPENCODE ->
                scope.launch { manager.start(this@LocalRuntimeService, EmbeddedComponent.OPENCODE) }
            AppConstants.ACTION_STOP_OPENCODE ->
                scope.launch { manager.stop(EmbeddedComponent.OPENCODE) }
            AppConstants.ACTION_START -> scope.launch {
                manager.start(this@LocalRuntimeService, EmbeddedComponent.N8N)
                manager.start(this@LocalRuntimeService, EmbeddedComponent.OPENCODE)
            }
            AppConstants.ACTION_STOP -> scope.launch {
                manager.stop(EmbeddedComponent.N8N)
                manager.stop(EmbeddedComponent.OPENCODE)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}