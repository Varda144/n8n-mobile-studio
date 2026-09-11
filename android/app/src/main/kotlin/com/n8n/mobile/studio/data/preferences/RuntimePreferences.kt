package com.n8n.mobile.studio.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.n8n.mobile.studio.core.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.runtimeStore by preferencesDataStore("runtime_settings")

/**
 * User-visible runtime settings: ports, auto-start, idle auto-stop.
 *
 * Kept separate from the REST instance store so resetting "instance" data can
 * never disturb runtime lifecycle preferences. Secrets never live here — they go
 * through [com.n8n.mobile.studio.security.SecureStorage].
 */
class RuntimePreferences(private val context: Context) {

    val config: Flow<AppConfig> = context.runtimeStore.data.map { prefs ->
        AppConfig(
            n8nPort = prefs[KEY_N8N_PORT] ?: AppConfig().n8nPort,
            openCodePort = prefs[KEY_OPENCODE_PORT] ?: AppConfig().openCodePort,
            n8nEnabled = prefs[KEY_N8N_ENABLED] ?: true,
            openCodeEnabled = prefs[KEY_OPENCODE_ENABLED] ?: true,
            n8nIdleStopMillis = prefs[KEY_N8N_IDLE] ?: AppConfig.DEFAULT_IDLE_STOP_MILLIS,
            openCodeIdleStopMillis = prefs[KEY_OPENCODE_IDLE] ?: AppConfig.DEFAULT_IDLE_STOP_MILLIS,
            autoStartN8n = prefs[KEY_AUTOSTART_N8N] ?: true,
            autoStartOpenCode = prefs[KEY_AUTOSTART_OPENCODE] ?: false,
        )
    }

    suspend fun setPorts(n8nPort: Int, openCodePort: Int) =
        context.runtimeStore.edit {
            it[KEY_N8N_PORT] = n8nPort.coerceIn(1024, 65535)
            it[KEY_OPENCODE_PORT] = openCodePort.coerceIn(1024, 65535)
        }

    suspend fun setEnabled(n8n: Boolean, openCode: Boolean) =
        context.runtimeStore.edit {
            it[KEY_N8N_ENABLED] = n8n
            it[KEY_OPENCODE_ENABLED] = openCode
        }

    suspend fun setAutoStart(n8n: Boolean, openCode: Boolean) =
        context.runtimeStore.edit {
            it[KEY_AUTOSTART_N8N] = n8n
            it[KEY_AUTOSTART_OPENCODE] = openCode
        }

    suspend fun setIdleStop(n8nMillis: Long, openCodeMillis: Long) =
        context.runtimeStore.edit {
            it[KEY_N8N_IDLE] = n8nMillis.coerceAtLeast(0)
            it[KEY_OPENCODE_IDLE] = openCodeMillis.coerceAtLeast(0)
        }

    private companion object {
        val KEY_N8N_PORT = intPreferencesKey("n8n_port")
        val KEY_OPENCODE_PORT = intPreferencesKey("opencode_port")
        val KEY_N8N_ENABLED = booleanPreferencesKey("n8n_enabled")
        val KEY_OPENCODE_ENABLED = booleanPreferencesKey("opencode_enabled")
        val KEY_AUTOSTART_N8N = booleanPreferencesKey("autostart_n8n")
        val KEY_AUTOSTART_OPENCODE = booleanPreferencesKey("autostart_opencode")
        val KEY_N8N_IDLE = longPreferencesKey("n8n_idle_stop_ms")
        val KEY_OPENCODE_IDLE = longPreferencesKey("opencode_idle_stop_ms")
    }
}
