package com.n8n.mobile.studio.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.n8n.mobile.studio.ui.theme.StudioPalette

/**
 * In-app GUI for a runtime that serves its own web interface.
 *
 * The URL is always the local loopback endpoint of the app's own process — the
 * WebView is a second view onto the same running instance the terminal talks to,
 * not a remote console. When the runtime is not serving, an explicit message is
 * shown instead of an empty frame.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RuntimeWebPanel(
    url: String?,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
    message: String? = null,
    reloadToken: Int = 0,
) {
    BrutalistSurface(modifier = modifier.fillMaxWidth(), shadow = false) {
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxWidth().height(height).padding(16.dp)) {
                Text(
                    message ?: "Runtime is not serving a local GUI yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioPalette.Muted,
                )
            }
            return@BrutalistSurface
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(height),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.loadsImagesAutomatically = true
                    settings.userAgentString = settings.userAgentString + " N8nMobileStudio"
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            update = { view ->
                if (view.url != url) view.loadUrl(url)
            },
        )
    }
}
