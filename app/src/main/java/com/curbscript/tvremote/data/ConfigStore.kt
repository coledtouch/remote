package com.curbscript.tvremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persisted settings shared by the app UI and the home-screen widget. */
data class Config(
    val onnHost: String = "",
    val onnPaired: Boolean = false,
    val vizioHost: String = "",
    val vizioPort: Int = 7345,
    val vizioToken: String = "",
    val samsungHost: String = "",
    val samsungToken: String = "",
    val navTrackpad: Boolean = false,
    val room: String = "living",
    val hubspaceRefresh: String = "",
    val hubspaceAccount: String = ""
) {
    val onnReady: Boolean get() = onnHost.isNotBlank() && onnPaired
    val vizioReady: Boolean get() = vizioHost.isNotBlank() && vizioToken.isNotBlank()
    val samsungReady: Boolean get() = samsungHost.isNotBlank() && samsungToken.isNotBlank()
    val livingReady: Boolean get() = vizioReady || onnReady
    val bedroomReady: Boolean get() = samsungReady
    val anyReady: Boolean get() = livingReady || bedroomReady
    val hubspaceReady: Boolean get() = hubspaceRefresh.isNotBlank()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "curb_remote")

class ConfigStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<Config> = store.data.map { p ->
        Config(
            onnHost = p[Keys.ONN_HOST] ?: "",
            onnPaired = p[Keys.ONN_PAIRED] ?: false,
            vizioHost = p[Keys.VIZIO_HOST] ?: "",
            vizioPort = p[Keys.VIZIO_PORT] ?: 7345,
            vizioToken = p[Keys.VIZIO_TOKEN] ?: "",
            samsungHost = p[Keys.SAMSUNG_HOST] ?: "",
            samsungToken = p[Keys.SAMSUNG_TOKEN] ?: "",
            navTrackpad = p[Keys.NAV_TRACKPAD] ?: false,
            room = p[Keys.ROOM] ?: "living",
            hubspaceRefresh = p[Keys.HS_REFRESH] ?: "",
            hubspaceAccount = p[Keys.HS_ACCOUNT] ?: ""
        )
    }

    suspend fun get(): Config = flow.first()

    suspend fun setOnn(host: String, paired: Boolean) =
        store.edit { it[Keys.ONN_HOST] = host.trim(); it[Keys.ONN_PAIRED] = paired }

    suspend fun setVizio(host: String, port: Int, token: String) =
        store.edit { it[Keys.VIZIO_HOST] = host.trim(); it[Keys.VIZIO_PORT] = port; it[Keys.VIZIO_TOKEN] = token }

    suspend fun setSamsung(host: String, token: String) =
        store.edit { it[Keys.SAMSUNG_HOST] = host.trim(); it[Keys.SAMSUNG_TOKEN] = token }

    suspend fun setNavTrackpad(v: Boolean) = store.edit { it[Keys.NAV_TRACKPAD] = v }
    suspend fun setRoom(r: String) = store.edit { it[Keys.ROOM] = r }
    suspend fun setHubspace(refresh: String, account: String) =
        store.edit { it[Keys.HS_REFRESH] = refresh; it[Keys.HS_ACCOUNT] = account }

    private object Keys {
        val ONN_HOST = stringPreferencesKey("onn_host")
        val ONN_PAIRED = booleanPreferencesKey("onn_paired")
        val VIZIO_HOST = stringPreferencesKey("vizio_host")
        val VIZIO_PORT = intPreferencesKey("vizio_port")
        val VIZIO_TOKEN = stringPreferencesKey("vizio_token")
        val SAMSUNG_HOST = stringPreferencesKey("samsung_host")
        val SAMSUNG_TOKEN = stringPreferencesKey("samsung_token")
        val NAV_TRACKPAD = booleanPreferencesKey("nav_trackpad")
        val ROOM = stringPreferencesKey("room")
        val HS_REFRESH = stringPreferencesKey("hs_refresh")
        val HS_ACCOUNT = stringPreferencesKey("hs_account")
    }
}
