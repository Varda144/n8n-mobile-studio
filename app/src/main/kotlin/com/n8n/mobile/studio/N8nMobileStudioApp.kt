package com.n8n.mobile.studio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.n8n.mobile.studio.core.Logger

class N8nMobileStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.init(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_RUNTIME,"Runtime",NotificationManager.IMPORTANCE_LOW).apply{description="n8n and OpenCode"})
            nm.createNotificationChannel(NotificationChannel(CH_ALERT,"Alerts",NotificationManager.IMPORTANCE_HIGH))
        }
    }
    companion object {
        const val CH_RUNTIME = "runtime"
        const val CH_ALERT = "alert"
        lateinit var instance: N8nMobileStudioApp; private set
    }
}
