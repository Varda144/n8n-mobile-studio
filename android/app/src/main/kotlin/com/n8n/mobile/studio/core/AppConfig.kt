package com.n8n.mobile.studio.core

/**
 * Immutable configuration for the embedded runtime environment.
 */
data class AppConfig(
    val runtimesDir: String = AppConstants.RUNTIME_DIR,
    val n8nPort: Int = AppConstants.N8N_PORT,
    val openCodePort: Int = AppConstants.OPENCODE_PORT,
) {
    val n8nEndpoint: String get() = "http://127.0.0.1:$n8nPort"
    val openCodeEndpoint: String get() = "http://127.0.0.1:$openCodePort"
}