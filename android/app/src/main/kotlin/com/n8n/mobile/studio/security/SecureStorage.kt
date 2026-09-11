package com.n8n.mobile.studio.security

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(context: Context) {
    private val prefs = context.getSharedPreferences("secure_secrets", Context.MODE_PRIVATE)
    private val keyAlias = "n8n_mobile_studio_master"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(256)
        return generator.generateKey()
    }

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, combined, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, combined, cipher.iv.size, encrypted.size)
        prefs.edit().putString(name, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? = runCatching {
        val combined = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        require(combined.size > 12)
        val iv = combined.copyOfRange(0, 12)
        val payload = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(payload), StandardCharsets.UTF_8)
    }.getOrNull()

    fun remove(name: String) { prefs.edit().remove(name).apply() }
}
