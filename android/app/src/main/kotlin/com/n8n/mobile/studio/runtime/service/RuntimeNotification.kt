package com.n8n.mobile.studio.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.n8n.mobile.studio.MainActivity
import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState

/**
 * The foreground notification for the runtime manager.
 *
 * It is the user's always-visible proof of what is running in the background, and
 * it carries the controls (start/stop per runtime, stop all) so a runtime can be
 * managed without opening the app — important on phones where the UI is likely to
 * have been evicted from memory.
 *
 * Built with platform notification APIs only (minSdk 26 has channels natively),
 * which keeps the app free of extra compatibility dependencies.
 */
class RuntimeNotification(private val context: Context) {

    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AppConstants.NOTIFICATION_CHANNEL_ID,
            AppConstants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Local n8n and OpenCode runtimes"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(statuses: List<EmbeddedComponentStatus>, preparing: Boolean = false): Notification {
        val builder = Notification.Builder(context, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(AppConstants.APP_NAME)
            .setContentText(summary(statuses, preparing))
            .setStyle(Notification.BigTextStyle().bigText(details(statuses, preparing)))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        statuses.forEach { status -> builder.addAction(toggleAction(status)) }
        builder.addAction(
            action(
                requestCode = ACTION_STOP_ALL,
                intentAction = AppConstants.ACTION_STOP,
                title = "Stop all",
                component = null,
            ),
        )
        return builder.build()
    }

    fun update(statuses: List<EmbeddedComponentStatus>, preparing: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(AppConstants.NOTIFICATION_ID, build(statuses, preparing)) }
    }

    private fun summary(statuses: List<EmbeddedComponentStatus>, preparing: Boolean): String {
        if (preparing && statuses.all { !it.state.isActive }) return "Preparing local runtime…"
        if (statuses.isEmpty()) return "No runtimes configured"
        return statuses.joinToString(" · ") { status ->
            val dot = when (status.state) {
                EmbeddedProcessState.RUNNING -> "●"
                EmbeddedProcessState.STARTING, EmbeddedProcessState.STOPPING -> "◐"
                EmbeddedProcessState.DEGRADED -> "◑"
                EmbeddedProcessState.FAILED -> "✕"
                EmbeddedProcessState.NOT_INSTALLED -> "—"
                EmbeddedProcessState.STOPPED -> "○"
            }
            "${status.component.label} $dot"
        }
    }

    private fun details(statuses: List<EmbeddedComponentStatus>, preparing: Boolean): String = buildString {
        if (preparing) appendLine("Preparing local runtime…")
        statuses.forEach { status ->
            append(status.component.label)
            append(": ")
            append(status.state.name.lowercase().replace('_', ' '))
            status.endpoint?.let { append(" @ ").append(it) }
            status.pid?.let { append(" (pid ").append(it).append(')') }
            appendLine()
            append("  ")
            appendLine(status.message)
        }
    }.trim()

    private fun toggleAction(status: EmbeddedComponentStatus): Notification.Action {
        val stop = status.state.isActive
        val action = when {
            stop -> RuntimeServiceController.stopAction(status.component)
            else -> RuntimeServiceController.startAction(status.component)
        }
        return action(
            requestCode = if (stop) 100 + status.component.ordinal else 200 + status.component.ordinal,
            intentAction = action,
            title = "${if (stop) "Stop" else "Start"} ${status.component.label}",
            component = status.component,
        )
    }

    private fun action(
        requestCode: Int,
        intentAction: String,
        title: String,
        component: EmbeddedComponent?,
    ): Notification.Action {
        val intent = Intent(context, LocalRuntimeService::class.java).setAction(intentAction)
        component?.let { intent.putExtra(AppConstants.EXTRA_COMPONENT, it.id) }
        val pending = PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(context, android.R.drawable.ic_media_play)
        return Notification.Action.Builder(icon, title, pending).build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val ACTION_STOP_ALL = 999
    }
}
