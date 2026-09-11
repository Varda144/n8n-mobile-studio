package com.n8n.mobile.studio.runtime.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.n8n.mobile.studio.core.AppConstants
import com.n8n.mobile.studio.runtime.EmbeddedComponent

/**
 * Starts and steers the foreground runtime service.
 *
 * The notification actions and the UI both go through here, so a tap on "Start
 * n8n" in the shade and a tap in the app follow exactly the same code path.
 */
object RuntimeServiceController {

    fun start(context: Context) = dispatch(context, AppConstants.ACTION_START)

    fun stop(context: Context) = dispatch(context, AppConstants.ACTION_STOP)

    fun restart(context: Context) = dispatch(context, AppConstants.ACTION_RESTART)

    fun start(context: Context, component: EmbeddedComponent) =
        dispatch(context, startAction(component), component)

    fun stop(context: Context, component: EmbeddedComponent) =
        dispatch(context, stopAction(component), component)

    fun restart(context: Context, component: EmbeddedComponent) =
        dispatch(context, restartAction(component), component)

    fun startAction(component: EmbeddedComponent): String = when (component) {
        EmbeddedComponent.N8N -> AppConstants.ACTION_START_N8N
        EmbeddedComponent.OPENCODE -> AppConstants.ACTION_START_OPENCODE
    }

    fun stopAction(component: EmbeddedComponent): String = when (component) {
        EmbeddedComponent.N8N -> AppConstants.ACTION_STOP_N8N
        EmbeddedComponent.OPENCODE -> AppConstants.ACTION_STOP_OPENCODE
    }

    fun restartAction(component: EmbeddedComponent): String = when (component) {
        EmbeddedComponent.N8N -> AppConstants.ACTION_RESTART_N8N
        EmbeddedComponent.OPENCODE -> AppConstants.ACTION_RESTART_OPENCODE
    }

    fun serviceIntent(context: Context, action: String): Intent =
        Intent(context, LocalRuntimeService::class.java).setAction(action)

    private fun dispatch(context: Context, action: String, component: EmbeddedComponent? = null) {
        val intent = serviceIntent(context, action)
        component?.let { intent.putExtra(AppConstants.EXTRA_COMPONENT, it.id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
