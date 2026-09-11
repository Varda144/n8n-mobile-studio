package com.n8n.mobile.studio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.n8n.mobile.studio.core.Logger
import com.n8n.mobile.studio.runtime.service.RuntimeServiceController

/**
 * The single activity. It starts the runtime service (so the local n8n/OpenCode
 * instances keep running in the background) and binds the GUI to it.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "POST_NOTIFICATIONS granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Foreground service first: the runtime must exist before the UI asks it
        // anything, otherwise the first screen would show "not installed".
        RuntimeServiceController.start(this)
        requestNotificationPermission()

        setContent { N8nMobileStudioApp() }
    }

    override fun onStart() {
        super.onStart()
        (application as? StudioApplication)?.runtimeClient?.ensureBound()
    }

    override fun onStop() {
        super.onStop()
        // Unbinding never stops the runtimes: they are owned by the service.
        (application as? StudioApplication)?.runtimeClient?.unbind()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
