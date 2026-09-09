package com.n8n.mobile.studio

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.n8n.mobile.studio/biometric"
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isBiometricAvailable" -> {
                    result.reply(checkBiometricAvailable())
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
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    private fun showBiometricPrompt(title: String, subtitle: String, result: MethodChannel.Result) {
        if (pendingResult != null) {
            result.error("ALREADY_RUNNING", "Biometric prompt already showing", null)
            return
        }

        val activity = activity
        if (activity !is FragmentActivity) {
            result.error("INVALID_ACTIVITY", "Activity is not a FragmentActivity", null)
            return
        }

        pendingResult = result

        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultAuth: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(resultAuth)
                pendingResult?.reply(mapOf("success" to true))
                pendingResult = null
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val error = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "user_cancel"
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "not_enrolled"
                    else -> "unknown"
                }
                pendingResult?.reply(mapOf("success" to false, "error" to error))
                pendingResult = null
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Called when a biometric is recognized but doesn't match.
                // Don't resolve yet — wait for onAuthenticationError or onAuthenticationSucceeded.
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Use PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
