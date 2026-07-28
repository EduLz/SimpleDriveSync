package com.example.drivesync.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.drivesync.data.*
import kotlinx.coroutines.flow.first
import java.io.File

class SyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsStore = SettingsDataStore(context)
        val apiKey = settingsStore.apiKey.first()
        val oauthToken = settingsStore.oauthToken.first()
        val authModeVal = settingsStore.authMode.first()
        val driveUrlVal = settingsStore.driveUrl.first()
        val localPathStr = settingsStore.localPath.first()

        val folderId = DriveApiClient.extractFolderId(driveUrlVal) ?: return Result.failure()
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

        // Show Foreground Notification
        val initialNotification = NotificationHelper.buildNotification(
            context,
            "Drive Sync",
            "Iniciando sincronización...",
            progressPercent = -1
        )
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID, initialNotification)
        }
        setForeground(foregroundInfo)

        // Step 1: Scan local
        val localItems = scanner.scan()
        val localKeysLower = localItems.keys.map { it.lowercase() }.toSet()

        // Step 2: List Drive
        val remoteResult = driveClient.listFolderRecursive(folderId) { currentFolder ->
            val notif = NotificationHelper.buildNotification(
                context,
                "Analizando Google Drive",
                "Listando: $currentFolder",
                progressPercent = -1
            )
            context.getSystemService(android.app.NotificationManager::class.java)?.notify(
                NotificationHelper.NOTIFICATION_ID, notif
            )
        }

        val remoteItems = when (remoteResult) {
            is ApiResult.Success -> remoteResult.data
            else -> return Result.retry()
        }

        // Step 3: Compare
        val toCreateFolders = mutableListOf<DriveItem>()
        val toDownloadFiles = mutableListOf<DriveItem>()

        for (item in remoteItems) {
            if (item.path.lowercase() !in localKeysLower) {
                if (item.type == ItemType.FOLDER) toCreateFolders.add(item)
                else toDownloadFiles.add(item)
            }
        }

        for (folder in toCreateFolders) {
            File(localDir, folder.path).mkdirs()
        }

        if (toDownloadFiles.isEmpty()) {
            val doneNotif = NotificationHelper.buildNotification(
                context,
                "Sincronización Completa",
                "Todos los archivos están al día",
                progressPercent = 100,
                isFinished = true
            )
            context.getSystemService(android.app.NotificationManager::class.java)?.notify(
                NotificationHelper.NOTIFICATION_ID, doneNotif
            )
            return Result.success()
        }

        var downloaded = 0
        val total = toDownloadFiles.size

        for (file in toDownloadFiles) {
            if (isStopped) return Result.failure()

            val destFile = File(localDir, file.path)
            val percent = (downloaded * 100) / total
            val notif = NotificationHelper.buildNotification(
                context,
                "Descargando ($downloaded/$total)",
                file.name,
                progressPercent = percent
            )
            context.getSystemService(android.app.NotificationManager::class.java)?.notify(
                NotificationHelper.NOTIFICATION_ID, notif
            )

            val downloadResult = driveClient.downloadFile(file.id, destFile, file.exportMime)
            if (downloadResult is ApiResult.Success) {
                downloaded++
            }
        }

        val finalNotif = NotificationHelper.buildNotification(
            context,
            "Sincronización Finalizada",
            "Descargados $downloaded de $total archivos",
            progressPercent = 100,
            isFinished = true
        )
        context.getSystemService(android.app.NotificationManager::class.java)?.notify(
            NotificationHelper.NOTIFICATION_ID, finalNotif
        )

        return Result.success()
    }
}
