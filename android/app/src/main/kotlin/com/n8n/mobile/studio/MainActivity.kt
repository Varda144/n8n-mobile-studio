package com.n8n.mobile.studio

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.n8n.mobile.studio/biometric"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isBiometricAvailable" -> {
                    result.success(false)
                }
                "authenticate" -> {
                    result.success(mapOf("success" to false, "error" to "not_available"))
                }
                else -> result.notImplemented()
            }
        }
    }
}
