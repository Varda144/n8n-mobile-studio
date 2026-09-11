package com.n8n.mobile.studio

import androidx.compose.runtime.Composable
import com.n8n.mobile.studio.ui.AppTheme
import com.n8n.mobile.studio.ui.SpatialSurface
import com.n8n.mobile.studio.ui.navigation.AppNavigation

@Composable
fun N8nMobileStudioApp() {
    AppTheme {
        SpatialSurface { AppNavigation() }
    }
}
