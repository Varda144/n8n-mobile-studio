package com.n8n.mobile.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.n8n.mobile.studio.runtime.service.RuntimeServiceController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { N8nMobileStudioApp() }
        RuntimeServiceController.start(this)
    }
}
