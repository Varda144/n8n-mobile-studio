package com.n8n.mobile.studio.security
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
class KeystoreManager(private val ctx:Context){
    companion object{ private const val ALIAS="n8n_mobile_studio_key" }
    private val ks:KeyStore by lazy{ KeyStore.getInstance("AndroidKeyStore").apply{load(null)} }
    fun getOrCreate():SecretKey{
        if(ks.containsAlias(ALIAS)) return (ks.getEntry(ALIAS,null) as KeyStore.SecretKeyEntry).secretKey
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        val spec=KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        kg.init(spec); return kg.generateKey()
    }
}
