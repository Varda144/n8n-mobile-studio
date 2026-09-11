package com.n8n.mobile.studio.n8n

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.n8n.mobile.studio.core.Logger

object N8nWebLauncher {
    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.e("N8nWebLauncher", "No browser available for $url", e)
        }
    }
}