package com.n8n.mobile.studio.local.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.n8n.mobile.studio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground supervisor for embedded runtimes. It keeps long-running local
 * processes alive without requiring Termux or an external server.
 */
class LocalRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = LocalRuntimeManager()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Local runtime ready"))
        scope.launch { manager.prepare(this@LocalRuntimeService) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_N8N -> scope.launch { manager.start(this@LocalRuntimeService, EmbeddedComponent.N8N) }
            ACTION_STOP_N8N -> scope.launch { manager.stop(EmbeddedComponent.N8N) }
            ACTION_START_OPENCODE -> scope.launch { manager.start(this@LocalRuntimeService, EmbeddedComponent.OPENCODE) }
            ACTION_STOP_OPENCODE -> scope.launch { manager.stop(EmbeddedComponent.OPENCODE) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Local Runtime", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("N8N Mobile Studio")
        .setContentText(text)
        .setOngoing(true)
        .build()

    companion object {
        const val ACTION_START_N8N = "com.n8n.mobile.studio.runtime.START_N8N"
        const val ACTION_STOP_N8N = "com.n8n.mobile.studio.runtime.STOP_N8N"
        const val ACTION_START_OPENCODE = "com.n8n.mobile.studio.runtime.START_OPENCODE"
        const val ACTION_STOP_OPENCODE = "com.n8n.mobile.studio.runtime.STOP_OPENCODE"
        private const val CHANNEL_ID = "local-runtime"
        private const val NOTIFICATION_ID = 2401
    }
}
