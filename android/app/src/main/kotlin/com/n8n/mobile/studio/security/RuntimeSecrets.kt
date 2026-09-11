package com.n8n.mobile.studio.security

import com.n8n.mobile.studio.runtime.opencode.OpenCodeConfig
import java.security.SecureRandom

/**
 * Secrets the local runtimes need, held in Keystore-backed storage.
 *
 *  - **n8n encryption key**: n8n encrypts stored credentials with it. The app
 *    generates it on device (32 random bytes, hex) and never logs or exports it.
 *  - **provider API keys**: only used to let the local OpenCode runtime reach an
 *    LLM provider the user explicitly configured.
 *
 * The runtime processes receive these values through their environment, which the
 * app builds per launch; nothing is written into the payload or into logs.
 */
class RuntimeSecrets(private val storage: SecureStorage = SecureStorageHolder.storage) {

    /** Returns the existing key or creates one. Stable across app restarts. */
    fun n8nEncryptionKey(): String = storage.get(KEY_N8N_ENCRYPTION)
        ?: generateHex(32).also { storage.put(KEY_N8N_ENCRYPTION, it) }

    fun rotateN8nEncryptionKey(): String = generateHex(32).also { storage.put(KEY_N8N_ENCRYPTION, it) }

    fun hasN8nEncryptionKey(): Boolean = storage.get(KEY_N8N_ENCRYPTION) != null

    fun providerKeys(): Map<String, String> = OpenCodeConfig.PROVIDER_KEYS
        .mapNotNull { name -> storage.get(providerKeyName(name))?.takeIf { it.isNotBlank() }?.let { name to it } }
        .toMap()

    fun putProviderKey(name: String, value: String) {
        require(name in OpenCodeConfig.PROVIDER_KEYS) { "unsupported provider key $name" }
        if (value.isBlank()) storage.remove(providerKeyName(name)) else storage.put(providerKeyName(name), value)
    }

    fun clearProviderKey(name: String) = storage.remove(providerKeyName(name))

    fun hasProviderKey(name: String): Boolean = storage.get(providerKeyName(name))?.isNotBlank() == true

    /** Never exposes values: used by the Settings screen. */
    fun configuredProviders(): List<String> = providerKeys().keys.toList()

    private fun providerKeyName(name: String) = "$KEY_PROVIDER_PREFIX$name"

    private fun generateHex(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return buffer.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_N8N_ENCRYPTION = "n8n_encryption_key"
        const val KEY_PROVIDER_PREFIX = "provider_"
    }
}

/**
 * The Android [SecureStorage] instance needs a Context; the runtime code is
 * context-free. The application sets the holder once at startup.
 */
object SecureStorageHolder {
    @Volatile
    var storage: SecureStorage = throw IllegalStateException(
        "SecureStorageHolder.storage must be initialised in StudioApplication.onCreate()",
    )

    fun initialise(storage: SecureStorage) {
        this.storage = storage
    }
}
