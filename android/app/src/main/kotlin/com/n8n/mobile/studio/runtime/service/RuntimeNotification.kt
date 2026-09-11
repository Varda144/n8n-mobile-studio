package com.n8n.mobile.studio.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.n8n.mobile.studio.core.AppConstants

class RuntimeNotification(private val context: Context) {
    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ID,
                "Local Runtime",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(text: String): Notification = NotificationCompat.Builder(
        context,
        AppConstants.NOTIFICATION_CHANNEL_ID,
    )
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(AppConstants.APP_NAME)
        .setContentText(text)
        .setOngoing(true)
        .build()
}