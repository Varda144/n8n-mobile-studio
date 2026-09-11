package com.n8n.mobile.studio.runtime.service

import android.content.Context
import android.content.Intent
import com.n8n.mobile.studio.core.AppConstants

object RuntimeServiceController {
    fun start(context: Context) {
        context.startForegroundService(serviceIntent(context, AppConstants.ACTION_START))
    }

    fun stop(context: Context) {
        context.stopService(serviceIntent(context, AppConstants.ACTION_STOP))
    }

    fun startN8n(context: Context) {
        context.startForegroundService(serviceIntent(context, AppConstants.ACTION_START_N8N))
    }

    fun stopN8n(context: Context) {
        context.startForegroundService(serviceIntent(context, AppConstants.ACTION_STOP_N8N))
    }

    fun startOpenCode(context: Context) {
        context.startForegroundService(serviceIntent(context, AppConstants.ACTION_START_OPENCODE))
    }

    fun stopOpenCode(context: Context) {
        context.startForegroundService(serviceIntent(context, AppConstants.ACTION_STOP_OPENCODE))
    }

    private fun serviceIntent(context: Context, action: String): Intent =
        Intent(context, LocalRuntimeService::class.java).setAction(action)
}