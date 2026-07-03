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

/** Persisted connection settings shared by the app UI and the home-screen widget. */
data class Config(
    val onnHost: String = "",
    val onnPaired: Boolean = false,
    val vizioHost: String = "",
    val vizioPort: Int = 7345,
    val vizioToken: String = ""
) {
    val onnReady: Boolean get() = onnHost.isNotBlank() && onnPaired
    val vizioReady: Boolean get() = vizioHost.isNotBlank() && vizioToken.isNotBlank()
    val anyReady: Boolean get() = onnReady || vizioReady
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
            vizioToken = p[Keys.VIZIO_TOKEN] ?: ""
        )
    }

    suspend fun get(): Config = flow.first()

    suspend fun setOnn(host: String, paired: Boolean) {
        store.edit {
            it[Keys.ONN_HOST] = host.trim()
            it[Keys.ONN_PAIRED] = paired
        }
    }

    suspend fun setVizio(host: String, port: Int, token: String) {
        store.edit {
            it[Keys.VIZIO_HOST] = host.trim()
            it[Keys.VIZIO_PORT] = port
            it[Keys.VIZIO_TOKEN] = token
        }
    }

    suspend fun clearOnn() = store.edit {
        it.remove(Keys.ONN_HOST); it.remove(Keys.ONN_PAIRED)
    }

    suspend fun clearVizio() = store.edit {
        it.remove(Keys.VIZIO_HOST); it.remove(Keys.VIZIO_PORT); it.remove(Keys.VIZIO_TOKEN)
    }

    private object Keys {
        val ONN_HOST = stringPreferencesKey("onn_host")
        val ONN_PAIRED = booleanPreferencesKey("onn_paired")
        val VIZIO_HOST = stringPreferencesKey("vizio_host")
        val VIZIO_PORT = intPreferencesKey("vizio_port")
        val VIZIO_TOKEN = stringPreferencesKey("vizio_token")
    }
}
