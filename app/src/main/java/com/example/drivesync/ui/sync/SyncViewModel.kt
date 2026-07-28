package com.example.drivesync.ui.sync

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivesync.data.*
import com.example.drivesync.worker.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SelectableDriveItem(
    val item: DriveItem,
    val isSelected: Boolean = true,
)

/** UI state for the sync screen */
sealed class SyncState {
    data object Idle : SyncState()
    data class Scanning(val message: String) : SyncState()
    data class FileSelection(
        val newFiles: List<SelectableDriveItem>,
        val existingFiles: List<DriveItem>,
        val totalSelectedBytes: Long,
        val remoteFolders: List<DriveItem>,
        val localDir: File,
        val driveClient: DriveApiClient,
        val rateLimiter: RateLimiter,
        val authModeVal: String,
        val startTime: Long,
    ) : SyncState()
    data class Syncing(
        val totalFiles: Int = 0,
        val downloaded: Int = 0,
        val skipped: Int = 0,
        val errors: Int = 0,
        val currentFile: String = "",
        val downloadedBytes: Long = 0,
        val waitingMessage: String = "",
        val fileDownloadedBytes: Long = 0,
        val fileTotalBytes: Long = 0,
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
                performScan()
            } catch (e: CancellationException) {
                NotificationHelper.cancelNotification(getApplication())
                _state.value = SyncState.Idle
            } catch (e: Exception) {
                NotificationHelper.cancelNotification(getApplication())
                if (_state.value !is SyncState.Idle) {
                    _state.value = SyncState.Error("Error: ${e.message}")
                }
            }
        }
    }

    fun toggleFileSelection(fileId: String) {
        val current = _state.value as? SyncState.FileSelection ?: return
        val updated = current.newFiles.map {
            if (it.item.id == fileId) it.copy(isSelected = !it.isSelected) else it
        }
        val totalSelectedBytes = updated.filter { it.isSelected }.sumOf { it.item.size }
        _state.value = current.copy(newFiles = updated, totalSelectedBytes = totalSelectedBytes)
    }

    fun selectAllFiles(select: Boolean) {
        val current = _state.value as? SyncState.FileSelection ?: return
        val updated = current.newFiles.map { it.copy(isSelected = select) }
        val totalSelectedBytes = updated.filter { it.isSelected }.sumOf { it.item.size }
        _state.value = current.copy(newFiles = updated, totalSelectedBytes = totalSelectedBytes)
    }

    fun startDownloadSelected() {
        val selectionState = _state.value as? SyncState.FileSelection ?: return
        val selectedFiles = selectionState.newFiles.filter { it.isSelected }.map { it.item }

        syncJob = viewModelScope.launch {
            try {
                performDownload(selectedFiles, selectionState)
            } catch (e: CancellationException) {
                NotificationHelper.cancelNotification(getApplication())
                _state.value = SyncState.Idle
            } catch (e: Exception) {
                NotificationHelper.cancelNotification(getApplication())
                if (_state.value !is SyncState.Idle) {
                    _state.value = SyncState.Error("Error: ${e.message}")
                }
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        syncJob = null
        NotificationHelper.cancelNotification(getApplication())
        _state.value = SyncState.Idle
    }

    private suspend fun performScan() {
        val apiKey = settingsStore.apiKey.first()
        val oauthToken = settingsStore.oauthToken.first()
        val authModeVal = settingsStore.authMode.first()
        val driveUrlVal = settingsStore.driveUrl.first()
        val localPathStr = settingsStore.localPath.first()

        val hasAuth = when (authModeVal) {
            "PUBLIC" -> true
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
            "API_KEY" -> RateLimiter(10, 2000, 4000, 15000, 25000)
            "PUBLIC" -> RateLimiter(120, 200, 400, 400, 800)
            else -> RateLimiter(1000, 0, 0, 0, 0)
        }

        val driveClient = when (authModeVal) {
            "OAUTH" -> DriveApiClient(oauthToken = oauthToken, rateLimiter = rateLimiter)
            "API_KEY" -> DriveApiClient(apiKey = apiKey, rateLimiter = rateLimiter)
            else -> DriveApiClient(apiKey = apiKey, oauthToken = oauthToken, rateLimiter = rateLimiter)
        }
        val scanner = LocalFileScanner(localDir)
        val startTime = System.currentTimeMillis()

        // Step 1: Verify access
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

        // Step 2: Scan local
        _state.value = SyncState.Scanning("Escaneando carpeta local...")
        val localItems = scanner.scan()
        val localKeysLower = localItems.keys.map { it.lowercase() }.toSet()

        // Step 3: List Drive
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
        val existingFiles = mutableListOf<DriveItem>()
        val newFiles = mutableListOf<SelectableDriveItem>()

        for (item in remoteItems) {
            if (item.type == ItemType.FOLDER) continue
            if (item.path.lowercase() in localKeysLower) {
                existingFiles.add(item)
            } else {
                newFiles.add(SelectableDriveItem(item, isSelected = true))
            }
        }

        if (newFiles.isEmpty()) {
            _state.value = SyncState.Done(
                downloaded = 0,
                skipped = existingFiles.size,
                errors = 0,
                downloadedBytes = 0,
                durationSeconds = (System.currentTimeMillis() - startTime) / 1000,
                totalDriveFiles = remoteItems.count { it.type == ItemType.FILE },
                totalDriveFolders = remoteFolders.size,
            )
            return
        }

        val totalSelectedBytes = newFiles.sumOf { it.item.size }
        _state.value = SyncState.FileSelection(
            newFiles = newFiles,
            existingFiles = existingFiles,
            totalSelectedBytes = totalSelectedBytes,
            remoteFolders = remoteFolders,
            localDir = localDir,
            driveClient = driveClient,
            rateLimiter = rateLimiter,
            authModeVal = authModeVal,
            startTime = startTime,
        )
    }

    private suspend fun performDownload(
        toDownloadFiles: List<DriveItem>,
        selectionState: SyncState.FileSelection,
    ) {
        val localDir = selectionState.localDir
        val driveClient = selectionState.driveClient
        val rateLimiter = selectionState.rateLimiter
        val authModeVal = selectionState.authModeVal
        val startTime = selectionState.startTime

        var downloaded = 0
        var errors = 0
        var totalBytes = 0L
        val total = toDownloadFiles.size
        val skippedCount = selectionState.existingFiles.size

        val waitMessageText = when (authModeVal) {
            "API_KEY" -> "Protección anti-ban activa (15-25s)..."
            "PUBLIC" -> "Descargando (ritmo adaptativo)..."
            else -> "Descargando..."
        }

        val notifManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        var idx = 0
        while (idx < total) {
            val file = toDownloadFiles[idx]
            val destFile = File(localDir, file.path)

            destFile.parentFile?.mkdirs()

            var fileBytesRead = 0L
            var fileTotal = file.size

            val percent = (downloaded * 100) / total
            val downloadNotif = NotificationHelper.buildNotification(
                getApplication(),
                "Descargando ($downloaded/$total)",
                file.name,
                progressPercent = percent
            )
            notifManager?.notify(NotificationHelper.NOTIFICATION_ID, downloadNotif)

            val progress = SyncState.Syncing(
                totalFiles = total,
                downloaded = downloaded,
                skipped = skippedCount,
                errors = errors,
                currentFile = file.name,
                downloadedBytes = totalBytes,
                waitingMessage = waitMessageText,
                fileDownloadedBytes = 0L,
                fileTotalBytes = fileTotal,
            )
            _state.value = progress

            val result = driveClient.downloadFile(
                file.id,
                destFile,
                file.exportMime,
                onProgress = { currentDownloaded, totalExpected ->
                    fileBytesRead = currentDownloaded
                    if (totalExpected > 0) fileTotal = totalExpected
                    _state.value = progress.copy(
                        fileDownloadedBytes = fileBytesRead,
                        fileTotalBytes = fileTotal
                    )
                }
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

        val doneNotif = NotificationHelper.buildNotification(
            getApplication(),
            "Sincronización Finalizada",
            "Descargados $downloaded de $total archivos",
            progressPercent = 100,
            isFinished = true
        )
        notifManager?.notify(NotificationHelper.NOTIFICATION_ID, doneNotif)

        _state.value = SyncState.Done(
            downloaded = downloaded,
            skipped = skippedCount,
            errors = errors,
            downloadedBytes = totalBytes,
            durationSeconds = (System.currentTimeMillis() - startTime) / 1000,
            totalDriveFiles = selectionState.existingFiles.size + selectionState.newFiles.size,
            totalDriveFolders = selectionState.remoteFolders.size,
        )
    }
}
