package com.n8n.mobile.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.n8n.mobile.studio.runtime.service.RuntimeClient
import com.n8n.mobile.studio.ui.LocalRuntimeClient
import com.n8n.mobile.studio.ui.navigation.AppNavigation
import com.n8n.mobile.studio.ui.theme.AppTheme
import com.n8n.mobile.studio.ui.theme.SpatialSurface

@Composable
fun N8nMobileStudioApp() {
    val client = rememberRuntimeClient()
    AppTheme {
        SpatialSurface {
            CompositionLocalProvider(LocalRuntimeClient provides client) {
                AppNavigation()
            }
        }
    }
}

/**
 * The single [RuntimeClient] lives on the application; screens read it through
 * [LocalRuntimeClient] so navigation stays exactly as it was and no screen has to
 * thread the runtime through its parameters.
 */
@Composable
fun rememberRuntimeClient(): RuntimeClient? {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as? StudioApplication)?.runtimeClient
    }
}
