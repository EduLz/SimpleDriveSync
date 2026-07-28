package com.example.drivesync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drive_sync_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val OAUTH_TOKEN = stringPreferencesKey("oauth_token")
        private val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        private val AUTH_MODE = stringPreferencesKey("auth_mode") // "API_KEY" or "OAUTH"
        private val DRIVE_URL = stringPreferencesKey("drive_url")
        private val LOCAL_PATH = stringPreferencesKey("local_path")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val oauthToken: Flow<String> = context.dataStore.data.map { it[OAUTH_TOKEN] ?: "" }
    val accountEmail: Flow<String> = context.dataStore.data.map { it[ACCOUNT_EMAIL] ?: "" }
    val authMode: Flow<String> = context.dataStore.data.map { it[AUTH_MODE] ?: "API_KEY" }
    val driveUrl: Flow<String> = context.dataStore.data.map { it[DRIVE_URL] ?: "" }
    val localPath: Flow<String> = context.dataStore.data.map { it[LOCAL_PATH] ?: "" }

    val hasConfig: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val hasAuth = !prefs[API_KEY].isNullOrBlank() || !prefs[OAUTH_TOKEN].isNullOrBlank()
        hasAuth && !prefs[DRIVE_URL].isNullOrBlank()
    }

    suspend fun saveSettings(
        apiKey: String,
        driveUrl: String,
        localPath: String,
        oauthToken: String = "",
        accountEmail: String = "",
        authMode: String = "API_KEY",
    ) {
        context.dataStore.edit { prefs ->
            prefs[API_KEY] = apiKey.trim()
            prefs[DRIVE_URL] = driveUrl.trim()
            prefs[LOCAL_PATH] = localPath.trim()
            prefs[OAUTH_TOKEN] = oauthToken.trim()
            prefs[ACCOUNT_EMAIL] = accountEmail.trim()
            prefs[AUTH_MODE] = authMode.trim()
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { it.clear() }
    }
}
