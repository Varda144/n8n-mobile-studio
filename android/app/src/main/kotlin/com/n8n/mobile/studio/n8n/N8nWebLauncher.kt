package com.n8n.mobile.studio.n8n

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus

/**
 * Opens the *same* local n8n instance in an external browser.
 *
 * The runtime binds to 127.0.0.1, so a browser on this device reaches the very
 * same process the in-app WebView and the terminal talk to — including its
 * workflows, credentials and executions. No tunnel, no remote server.
 */
object N8nWebLauncher {

    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.e(TAG, "No browser available for $url", e)
        }
    }

    /** Convenience for callers holding a runtime status. */
    fun openStatus(context: Context, status: EmbeddedComponentStatus): Boolean {
        val endpoint = status.endpoint ?: return false
        open(context, endpoint)
        return true
    }

    private const val TAG = "N8nWebLauncher"
}
