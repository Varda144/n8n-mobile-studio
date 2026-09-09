package com.n8n.mobile.studio

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterFragmentActivity() {

    private val CHANNEL = "com.n8n.mobile.studio/biometric"
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isBiometricAvailable" -> {
                    result.success(checkBiometricAvailable())
                }
                "authenticate" -> {
                    val title = call.argument<String>("title") ?: "Authenticate"
                    val subtitle = call.argument<String>("subtitle") ?: "Verify your identity"
                    showBiometricPrompt(title, subtitle, result)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun checkBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        val authResult = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return authResult == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt(title: String, subtitle: String, result: MethodChannel.Result) {
        if (pendingResult != null) {
            result.error("ALREADY_RUNNING", "Biometric prompt already showing", null)
            return
        }

        val currentActivity = activity
        if (currentActivity == null) {
            result.error("NO_ACTIVITY", "No activity available", null)
            return
        }

        pendingResult = result
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(authResult: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(authResult)
                val res = pendingResult
                pendingResult = null
                res?.success(mapOf("success" to true))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val errorType = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> "user_cancel"
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE -> "not_enrolled"
                    else -> "unknown"
                }
                val res = pendingResult
                pendingResult = null
                res?.success(mapOf("success" to false, "error" to errorType))
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Use PIN")
            .build()

        val fragmentActivity = currentActivity as? androidx.fragment.app.FragmentActivity
        if (fragmentActivity == null) {
            result.error("INVALID_ACTIVITY", "Activity is not FragmentActivity", null)
            return
        }

        val biometricPrompt = BiometricPrompt(fragmentActivity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
