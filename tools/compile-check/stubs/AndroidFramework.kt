@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch")

package android.app

import java.io.File

/**
 * Offline check stub: the slice of the Android framework this app touches.
 *
 * Only signatures matter here — the classes are never executed. They exist so the
 * service, runtime and terminal layers can be type-checked on a machine without
 * the Android SDK. Gradle compiles against the real framework in CI.
 */
class Notification private constructor(
    val channelId: String,
    val title: CharSequence?,
    val text: CharSequence?,
    val bigText: CharSequence?,
    val ongoing: Boolean,
    val actions: List<Action>,
) {
    class Builder(val context: Any?, val channelId: String) {
        private var title: CharSequence? = null
        private var text: CharSequence? = null
        private var bigText: CharSequence? = null
        private var ongoing = false
        private var actions = mutableListOf<Action>()

        fun setSmallIcon(icon: Int): Builder = this
        fun setContentTitle(title: CharSequence?): Builder = apply { this.title = title }
        fun setContentText(text: CharSequence?): Builder = apply { this.text = text }
        fun setStyle(style: Style): Builder = this
        fun setOngoing(value: Boolean): Builder = apply { ongoing = value }
        fun setSilent(value: Boolean): Builder = this
        fun setOnlyAlertOnce(value: Boolean): Builder = this
        fun setContentIntent(intent: PendingIntent?): Builder = this
        fun addAction(action: Action): Builder = apply { actions.add(action) }
        fun setSubText(text: CharSequence?): Builder = this
        fun setShowWhen(value: Boolean): Builder = this
        fun build(): Notification = Notification(channelId, title, text, bigText, ongoing, actions)
    }

    interface Style

    class BigTextStyle : Style {
        fun bigText(text: CharSequence?): BigTextStyle = this
    }

    class Action(val title: CharSequence?) {
        class Builder(private val icon: Any?, private val title: CharSequence?, private val intent: PendingIntent?) {
            fun build(): Action = Action(title)
        }
    }

    companion object {
        const val PRIORITY_LOW = -1
    }
}

class NotificationChannel(
    val id: String,
    val name: CharSequence?,
    val importance: Int,
) {
    var description: String? = null

    fun setShowBadge(value: Boolean) {}
    fun enableVibration(value: Boolean) {}
}

open class NotificationManager {
    fun createNotificationChannel(channel: NotificationChannel) {}
    fun notify(id: Int, notification: Notification) {}
    fun cancel(id: Int) {}
    fun areNotificationsEnabled(): Boolean = true

    companion object {
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
    }
}

class PendingIntent {
    companion object {
        const val FLAG_UPDATE_CURRENT = 134217728
        const val FLAG_IMMUTABLE = 67108864
        fun getActivity(context: Any?, requestCode: Int, intent: Any?, flags: Int): PendingIntent = PendingIntent()
        fun getService(context: Any?, requestCode: Int, intent: Any?, flags: Int): PendingIntent = PendingIntent()
    }
}

open class Service : android.content.ContextWrapper() {
    override fun getApplicationContext(): android.content.Context = this
    open fun onCreate() {}
    open fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int = START_STICKY
    open fun onBind(intent: android.content.Intent?): android.os.IBinder? = null
    open fun onUnbind(intent: android.content.Intent?): Boolean = false
    open fun onDestroy() {}
    open fun onTaskRemoved(rootIntent: android.content.Intent?) {}
    open fun onLowMemory() {}
    open fun onTrimMemory(level: Int) {}
    fun startForeground(id: Int, notification: Notification) {}
    fun startForeground(id: Int, notification: Notification, type: Int) {}
    fun stopForeground(removeNotification: Boolean) {}
    fun stopForeground(flags: Int) {}
    open fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
    fun stopSelf() {}
    fun stopSelf(startId: Int) {}
    fun stopSelfResult(startId: Int): Boolean = true

    companion object {
        const val START_STICKY = 1
        const val START_NOT_STICKY = 2
        const val START_REDELIVER_INTENT = 3
        const val START_STICKY_COMPATIBILITY = 0
        const val STOP_FOREGROUND_REMOVE = 1
        const val STOP_FOREGROUND_DETACH = 2
    }
}

open class Activity : android.content.ContextWrapper() {
    override fun getApplicationContext(): android.content.Context = this
    open fun runOnUiThread(action: Runnable) {}
    open fun finish() {}
    open fun onBackPressed() {}
}

class ActivityManager {
    /** Java getter getMemoryClass() is seen as a property by Kotlin. */
    val memoryClass: Int = 192

    class MemoryInfo {
        var totalMem: Long = 0
        var availMem: Long = 0
        var threshold: Long = 0
        var lowMemory: Boolean = false
    }


    fun getMemoryInfo(outInfo: MemoryInfo) {
        Runtime.getRuntime().let {
            val total = it.maxMemory()
            outInfo.totalMem = total
            outInfo.availMem = it.freeMemory()
            outInfo.threshold = total / 16
        }
    }

    companion object {
        const val MEMORY_CLASS_NOTHING: Int = 0
    }
}

class Application : android.content.ContextWrapper() {
    override fun getApplicationContext(): android.content.Context = this
    open fun onCreate() {}
    open fun onTerminate() {}
    open fun onLowMemory() {}
    open fun onTrimMemory(level: Int) {}
}
