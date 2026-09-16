package com.n8n.mobile.studio.runtime.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.n8n.mobile.studio.MainActivity
import com.n8n.mobile.studio.N8nMobileStudioApp
import com.n8n.mobile.studio.R

class RuntimeNotification(private val ctx:Context){
    fun foreground(title:String, content:String): Notification {
        val pi = PendingIntent.getActivity(ctx,0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, N8nMobileStudioApp.CH_RUNTIME)
            .setContentTitle(title).setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi).setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
