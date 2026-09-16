package com.n8n.mobile.studio.data.preferences
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
private val Context.prefs by preferencesDataStore("studio_prefs")
class PreferencesStore(private val ctx:Context){
    private val N8N_PORT = intPreferencesKey("n8n_port")
    private val OC_PORT = intPreferencesKey("opencode_port")
    val n8nPort = ctx.prefs.data.map{ it[N8N_PORT]?:5678 }
    val ocPort = ctx.prefs.data.map{ it[OC_PORT]?:8080 }
    suspend fun setN8nPort(v:Int){ ctx.prefs.edit{it[N8N_PORT]=v} }
}
