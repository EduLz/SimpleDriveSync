package com.example.drivesync.ui.setup

import android.accounts.Account
import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivesync.data.DriveApiClient
import com.example.drivesync.data.SettingsDataStore
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SetupUiState(
    val apiKey: String = "",
    val oauthToken: String = "",
    val accountEmail: String = "",
    val authMode: String = "OAUTH", // "OAUTH" or "API_KEY"
    val driveUrl: String = "",
    val localPath: String = "",
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
            return File(downloads, "Tamashis Project").absolutePath
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
            val hasAuth = if (authMode == "OAUTH") oauthToken.isNotBlank() else apiKey.isNotBlank()
            _uiState.value = _uiState.value.copy(
                apiKey = apiKey,
                oauthToken = oauthToken,
                accountEmail = accountEmail,
                authMode = authMode,
                driveUrl = driveUrl,
                localPath = localPath,
                isValid = hasAuth && driveUrl.isNotBlank(),
            )
        }
    }

    fun setAuthMode(mode: String) {
        val state = _uiState.value
        val hasAuth = if (mode == "OAUTH") state.oauthToken.isNotBlank() else state.apiKey.isNotBlank()
        _uiState.value = state.copy(
            authMode = mode,
            isValid = hasAuth && state.driveUrl.isNotBlank(),
            errorMessage = null,
        )
    }

    fun onWebTokenCaptured(token: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            oauthToken = token,
            accountEmail = "Cuenta de Google (OAuth Web)",
            authMode = "OAUTH",
            isValid = state.driveUrl.isNotBlank(),
            errorMessage = null,
        )
    }

    fun updateOAuthToken(token: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            oauthToken = token,
            authMode = "OAUTH",
            isValid = token.isNotBlank() && state.driveUrl.isNotBlank(),
            errorMessage = null,
        )
    }

    fun updateApiKey(value: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            apiKey = value,
            isValid = value.isNotBlank() && state.driveUrl.isNotBlank(),
            errorMessage = null,
        )
    }

    fun updateDriveUrl(value: String) {
        val state = _uiState.value
        val hasAuth = if (state.authMode == "OAUTH") state.oauthToken.isNotBlank() else state.apiKey.isNotBlank()
        _uiState.value = state.copy(
            driveUrl = value,
            isValid = hasAuth && value.isNotBlank(),
            errorMessage = null,
        )
    }

    fun updateLocalPath(value: String) {
        _uiState.value = _uiState.value.copy(localPath = value, errorMessage = null)
    }

    fun setAuthError(msg: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = msg,
            isSaving = false
        )
    }

    fun onGoogleSignInSuccess(account: Account, email: String) {
        viewModelScope.launch {
            try {
                val scope = "oauth2:https://www.googleapis.com/auth/drive.readonly"
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(getApplication(), account, scope)
                }
                val state = _uiState.value
                _uiState.value = state.copy(
                    oauthToken = token,
                    accountEmail = email,
                    authMode = "OAUTH",
                    isValid = state.driveUrl.isNotBlank(),
                    errorMessage = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error obteniendo token de Google: ${e.message}\n(Nota: OAuth nativo de Android requiere registrar la app o usar Client ID Web)"
                )
            }
        }
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
            )
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
