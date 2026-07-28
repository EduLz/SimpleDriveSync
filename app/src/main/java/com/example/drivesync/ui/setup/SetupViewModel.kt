package com.example.drivesync.ui.setup

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivesync.data.DriveApiClient
import com.example.drivesync.data.SettingsDataStore
import com.example.drivesync.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class SetupUiState(
    val apiKey: String = "",
    val oauthToken: String = "",
    val accountEmail: String = "",
    val authMode: String = "PUBLIC", // "PUBLIC", "OAUTH", "API_KEY"
    val driveUrl: String = "",
    val localPath: String = "",
    val syncInterval: String = "OFF", // "OFF", "6H", "12H", "24H"
    val wifiOnly: Boolean = false,
    val isSaving: Boolean = false,
    val isValid: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    companion object {
        fun getDefaultDownloadPath(): String {
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            return File(downloads, "SimpleDriveSync").absolutePath
        }
    }

    init {
        viewModelScope.launch {
            val apiKey = settingsStore.apiKey.first()
            val oauthToken = settingsStore.oauthToken.first()
            val accountEmail = settingsStore.accountEmail.first()
            val authMode = settingsStore.authMode.first()
            val driveUrl = settingsStore.driveUrl.first()
            val localPath = settingsStore.localPath.first().ifBlank {
                getDefaultDownloadPath()
            }
            val syncInterval = settingsStore.syncInterval.first()
            val wifiOnly = settingsStore.wifiOnly.first()

            val hasAuth = when (authMode) {
                "PUBLIC" -> true
                "OAUTH" -> oauthToken.isNotBlank()
                "API_KEY" -> apiKey.isNotBlank()
                else -> true
            }
            _uiState.value = _uiState.value.copy(
                apiKey = apiKey,
                oauthToken = oauthToken,
                accountEmail = accountEmail,
                authMode = authMode,
                driveUrl = driveUrl,
                localPath = localPath,
                syncInterval = syncInterval,
                wifiOnly = wifiOnly,
                isValid = hasAuth && driveUrl.isNotBlank(),
            )
        }
    }

    fun setAuthMode(mode: String) {
        val state = _uiState.value
        val hasAuth = when (mode) {
            "PUBLIC" -> true
            "OAUTH" -> state.oauthToken.isNotBlank()
            "API_KEY" -> state.apiKey.isNotBlank()
            else -> true
        }
        _uiState.value = state.copy(
            authMode = mode,
            isValid = hasAuth && state.driveUrl.isNotBlank(),
            errorMessage = null,
        )
    }

    fun setSyncInterval(interval: String) {
        _uiState.value = _uiState.value.copy(syncInterval = interval)
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        _uiState.value = _uiState.value.copy(wifiOnly = wifiOnly)
    }

    fun onWebTokenCaptured(token: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            oauthToken = token,
            accountEmail = "Obteniendo cuenta...",
            authMode = "OAUTH",
            isValid = state.driveUrl.isNotBlank(),
            errorMessage = null,
        )

        viewModelScope.launch(Dispatchers.IO) {
            val fetchedEmail = fetchUserInfoEmail(token)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    accountEmail = fetchedEmail ?: "Cuenta de Google (OAuth Web)"
                )
            }
        }
    }

    private fun fetchUserInfoEmail(token: String): String? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $token")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                json.optString("email").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun updateApiKey(apiKey: String) {
        val state = _uiState.value
        val hasAuth = if (state.authMode == "API_KEY") apiKey.isNotBlank() else true
        _uiState.value = state.copy(
            apiKey = apiKey,
            isValid = hasAuth && state.driveUrl.isNotBlank(),
            errorMessage = null,
        )
    }

    fun updateOAuthToken(token: String) {
        val state = _uiState.value
        val hasAuth = if (state.authMode == "OAUTH") token.isNotBlank() else true
        _uiState.value = state.copy(
            oauthToken = token,
            isValid = hasAuth && state.driveUrl.isNotBlank(),
            errorMessage = null,
        )

        if (token.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val fetchedEmail = fetchUserInfoEmail(token)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        accountEmail = fetchedEmail ?: "Cuenta de Google (OAuth Web)"
                    )
                }
            }
        }
    }

    fun updateDriveUrl(url: String) {
        val state = _uiState.value
        val hasAuth = when (state.authMode) {
            "PUBLIC" -> true
            "OAUTH" -> state.oauthToken.isNotBlank()
            "API_KEY" -> state.apiKey.isNotBlank()
            else -> true
        }
        _uiState.value = state.copy(
            driveUrl = url,
            isValid = hasAuth && url.isNotBlank(),
            errorMessage = null,
        )
    }

    fun updateLocalPath(path: String) {
        _uiState.value = _uiState.value.copy(localPath = path)
    }

    fun onFolderSelected(uri: Uri) {
        val path = extractPathFromUri(uri)
        if (path != null) {
            _uiState.value = _uiState.value.copy(localPath = path, errorMessage = null)
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No se pudo obtener la ruta de la carpeta seleccionada. Ingresa la ruta manualmente."
            )
        }
    }

    fun consumeSavedSuccessfully() {
        _uiState.value = _uiState.value.copy(savedSuccessfully = false)
    }

    fun saveAndValidate() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isSaving = true, errorMessage = null)

            val folderId = DriveApiClient.extractFolderId(state.driveUrl)
            if (folderId == null) {
                _uiState.value = state.copy(
                    isSaving = false,
                    errorMessage = "URL de Drive inválida. Usa formato: https://drive.google.com/drive/folders/..."
                )
                return@launch
            }

            settingsStore.saveSettings(
                apiKey = state.apiKey,
                driveUrl = state.driveUrl,
                localPath = state.localPath,
                oauthToken = state.oauthToken,
                accountEmail = state.accountEmail,
                authMode = state.authMode,
                syncInterval = state.syncInterval,
                wifiOnly = state.wifiOnly,
            )

            WorkScheduler.updateSchedule(getApplication(), state.syncInterval, state.wifiOnly)

            _uiState.value = state.copy(isSaving = false, savedSuccessfully = true)
        }
    }

    private fun extractPathFromUri(uri: Uri): String? {
        val docId = uri.lastPathSegment ?: return null

        if (docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            return File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
        }

        val parts = docId.split(":")
        if (parts.size == 2) {
            val storageId = parts[0]
            val relativePath = parts[1]
            val sdPaths = listOf(
                "/storage/$storageId",
                "/mnt/media_rw/$storageId",
            )
            for (sdPath in sdPaths) {
                val dir = File(sdPath, relativePath)
                if (dir.exists() || File(sdPath).exists()) {
                    return dir.absolutePath
                }
            }
        }

        return null
    }
}
