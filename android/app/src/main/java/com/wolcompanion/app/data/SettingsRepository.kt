package com.wolcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pulse_settings")

/**
 * Single source of truth for persisted settings. Everything the user enters in
 * Setup is written here and survives restarts — they never re-enter it.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PC_NAME = stringPreferencesKey("pc_name")
        val PC_MAC = stringPreferencesKey("pc_mac")
        val PC_BROADCAST = stringPreferencesKey("pc_broadcast")
        val PC_IP = stringPreferencesKey("pc_ip")
        val PC_PORT = intPreferencesKey("pc_port")
        val HOME_SSID = stringPreferencesKey("home_ssid")
        val AUTO_WAKE = booleanPreferencesKey("auto_wake")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            pc = PcDevice(
                name = p[Keys.PC_NAME] ?: "",
                mac = p[Keys.PC_MAC] ?: "",
                broadcastAddress = p[Keys.PC_BROADCAST] ?: "255.255.255.255",
                ip = p[Keys.PC_IP] ?: "",
                port = p[Keys.PC_PORT] ?: 9,
            ),
            homeSsid = p[Keys.HOME_SSID] ?: "",
            autoWakeEnabled = p[Keys.AUTO_WAKE] ?: false,
            setupComplete = p[Keys.SETUP_DONE] ?: false,
        )
    }

    suspend fun savePc(pc: PcDevice) {
        context.dataStore.edit { p ->
            p[Keys.PC_NAME] = pc.name
            p[Keys.PC_MAC] = pc.mac
            p[Keys.PC_BROADCAST] = pc.broadcastAddress
            p[Keys.PC_IP] = pc.ip
            p[Keys.PC_PORT] = pc.port
        }
    }

    suspend fun saveHomeSsid(ssid: String) {
        context.dataStore.edit { it[Keys.HOME_SSID] = ssid }
    }

    suspend fun setAutoWake(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_WAKE] = enabled }
    }

    suspend fun setSetupComplete(done: Boolean) {
        context.dataStore.edit { it[Keys.SETUP_DONE] = done }
    }
}
