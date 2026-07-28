package com.example.drivesync.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivesync.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** UI state for the sync screen */
sealed class SyncState {
    data object Idle : SyncState()
    data class Scanning(val message: String) : SyncState()
    data class Syncing(
        val totalFiles: Int = 0,
        val downloaded: Int = 0,
        val skipped: Int = 0,
        val errors: Int = 0,
        val currentFile: String = "",
        val downloadedBytes: Long = 0,
        val waitingMessage: String = "",
    ) : SyncState()
    data class Paused(
        val reason: String,
        val remainingMinutes: Int,
        val syncProgress: Syncing,
    ) : SyncState()
    data class Done(
        val downloaded: Int,
        val skipped: Int,
        val errors: Int,
        val downloadedBytes: Long,
        val durationSeconds: Long,
        val totalDriveFiles: Int,
        val totalDriveFolders: Int,
    ) : SyncState()
    data class Error(val message: String) : SyncState()
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsDataStore(application)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    val authMode: StateFlow<String> = settingsStore.authMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "PUBLIC"
    )

    val accountEmail: StateFlow<String> = settingsStore.accountEmail.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val driveUrl: StateFlow<String> = settingsStore.driveUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    private var syncJob: Job? = null

    fun startSync() {
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
            try {
                performSync()
            } catch (e: Exception) {
                _state.value = SyncState.Error("Error: ${e.message}")
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        _state.value = SyncState.Idle
    }

    private suspend fun performSync() {
        val apiKey = settingsStore.apiKey.first()
        val oauthToken = settingsStore.oauthToken.first()
        val authModeVal = settingsStore.authMode.first()
        val driveUrlVal = settingsStore.driveUrl.first()
        val localPathStr = settingsStore.localPath.first()

        val hasAuth = when (authModeVal) {
            "PUBLIC" -> true // Public mode doesn't require user login!
            "OAUTH" -> oauthToken.isNotBlank()
            "API_KEY" -> apiKey.isNotBlank()
            else -> true
        }

        if (!hasAuth || driveUrlVal.isBlank()) {
            _state.value = SyncState.Error("Configuración incompleta. Ve a Ajustes.")
            return
        }

        val folderId = DriveApiClient.extractFolderId(driveUrlVal)
        if (folderId == null) {
            _state.value = SyncState.Error("URL de Drive inválida")
            return
        }

        val localDir = File(localPathStr)
        val rateLimiter = when (authModeVal) {
            "API_KEY" -> {
                // Conservative Anti-Ban protection for API Key mode
                RateLimiter(
                    maxRequestsPerMinute = 10,
                    apiDelayMinMs = 2000,
                    apiDelayMaxMs = 4000,
                    downloadDelayMinMs = 15000,
                    downloadDelayMaxMs = 25000,
                )
            }
            "PUBLIC" -> {
                // Smart micro-pause pacing (~500ms) for Public links to prevent Google's unauthenticated burst block
                RateLimiter(
                    maxRequestsPerMinute = 120,
                    apiDelayMinMs = 200,
                    apiDelayMaxMs = 400,
                    downloadDelayMinMs = 400,
                    downloadDelayMaxMs = 800,
                )
            }
            else -> {
                // Unthrottled 0ms maximum speed for OAuth 2.0
                RateLimiter(
                    maxRequestsPerMinute = 1000,
                    apiDelayMinMs = 0,
                    apiDelayMaxMs = 0,
                    downloadDelayMinMs = 0,
                    downloadDelayMaxMs = 0,
                )
            }
        }

        val driveClient = when (authModeVal) {
            "OAUTH" -> DriveApiClient(oauthToken = oauthToken, rateLimiter = rateLimiter)
            "API_KEY" -> DriveApiClient(apiKey = apiKey, rateLimiter = rateLimiter)
            else -> DriveApiClient(apiKey = apiKey, oauthToken = oauthToken, rateLimiter = rateLimiter)
        }
        val scanner = LocalFileScanner(localDir)

        val startTime = System.currentTimeMillis()

        // ─── Step 1: Verify access ─────────────────────────
        _state.value = SyncState.Scanning("Verificando acceso a Google Drive...")
        when (val result = driveClient.verifyAccess(folderId)) {
            is ApiResult.Success -> { /* OK */ }
            is ApiResult.Error -> {
                _state.value = SyncState.Error("No se pudo acceder: ${result.message}")
                return
            }
            is ApiResult.QuotaExhausted -> {
                _state.value = SyncState.Error("Quota agotada. Intenta más tarde.")
                return
            }
        }

        // ─── Step 2: Scan local ────────────────────────────
        _state.value = SyncState.Scanning("Escaneando carpeta local...")
        val localItems = scanner.scan()
        val localKeysLower = localItems.keys.map { it.lowercase() }.toSet()

        // ─── Step 3: List Drive ────────────────────────────
        _state.value = SyncState.Scanning("Listando archivos de Google Drive...")
        val remoteItems = when (val result = driveClient.listFolderRecursive(folderId) { folder ->
            _state.value = SyncState.Scanning("Listando: $folder...")
        }) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> {
                _state.value = SyncState.Error("Error listando Drive: ${result.message}")
                return
            }
            is ApiResult.QuotaExhausted -> {
                _state.value = SyncState.Error("Quota agotada al listar. Intenta más tarde.")
                return
            }
        }

        val remoteFolders = remoteItems.filter { it.type == ItemType.FOLDER }
        val remoteFiles = remoteItems.filter { it.type == ItemType.FILE }

        // ─── Step 4: Compare ───────────────────────────────
        _state.value = SyncState.Scanning("Comparando Drive vs Local...")

        val toCreateFolders = mutableListOf<DriveItem>()
        val toDownloadFiles = mutableListOf<DriveItem>()
        var skippedCount = 0

        for (item in remoteItems) {
            if (item.path.lowercase() in localKeysLower) {
                skippedCount++
            } else {
                if (item.type == ItemType.FOLDER) toCreateFolders.add(item)
                else toDownloadFiles.add(item)
            }
        }

        // ─── Step 5: Create folders ────────────────────────
        for (folder in toCreateFolders) {
            File(localDir, folder.path).mkdirs()
        }

        // ─── Step 6: Download files ────────────────────────
        if (toDownloadFiles.isEmpty()) {
            _state.value = SyncState.Done(
                downloaded = 0,
                skipped = skippedCount,
                errors = 0,
                downloadedBytes = 0,
                durationSeconds = (System.currentTimeMillis() - startTime) / 1000,
                totalDriveFiles = remoteFiles.size,
                totalDriveFolders = remoteFolders.size,
            )
            return
        }

        var downloaded = 0
        var errors = 0
        var totalBytes = 0L
        val total = toDownloadFiles.size

        val waitMessageText = when (authModeVal) {
            "API_KEY" -> "Protección Anti-Ban activa (15-25s)..."
            "PUBLIC" -> "Ritmo adaptativo inteligente (~500ms)..."
            else -> "Descargando a máxima velocidad (OAuth)..."
        }

        var idx = 0
        while (idx < total) {
            val file = toDownloadFiles[idx]
            val destFile = File(localDir, file.path)

            val progress = SyncState.Syncing(
                totalFiles = total,
                downloaded = downloaded,
                skipped = skippedCount,
                errors = errors,
                currentFile = file.name,
                downloadedBytes = totalBytes,
                waitingMessage = waitMessageText,
            )
            _state.value = progress

            val result = driveClient.downloadFile(
                file.id, destFile, file.exportMime
            )

            when (result) {
                is ApiResult.Success -> {
                    downloaded++
                    totalBytes += result.data
                    idx++
                }
                is ApiResult.QuotaExhausted -> {
                    val cooldownMs = rateLimiter.cooldownDurationMs
                    var remaining = cooldownMs
                    while (remaining > 0) {
                        _state.value = SyncState.Paused(
                            reason = "Quota de Google agotada",
                            remainingMinutes = (remaining / 60000).toInt(),
                            syncProgress = progress.copy(
                                waitingMessage = "Pausa: ${remaining / 60000} min restantes"
                            ),
                        )
                        delay(60000)
                        remaining -= 60000
                    }
                }
                is ApiResult.Error -> {
                    errors++
                    idx++
                }
            }
        }

        _state.value = SyncState.Done(
            downloaded = downloaded,
            skipped = skippedCount,
            errors = errors,
            downloadedBytes = totalBytes,
            durationSeconds = (System.currentTimeMillis() - startTime) / 1000,
            totalDriveFiles = remoteFiles.size,
            totalDriveFolders = remoteFolders.size,
        )
    }
}
