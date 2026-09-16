package com.n8n.mobile.studio.security
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
class SecureStorage(ctx:Context){
    private val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(ctx,"n8n_sec", mk, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun save(k:String,v:String){ prefs.edit().putString(k,v).apply() }
    fun get(k:String,d:String=""):String = prefs.getString(k,d)?:d
    fun remove(k:String){ prefs.edit().remove(k).apply()}
}
