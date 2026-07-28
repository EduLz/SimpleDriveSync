package com.example.drivesync.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Result of an API operation */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
    data object QuotaExhausted : ApiResult<Nothing>()
}

/** Represents a file or folder in Google Drive */
data class DriveItem(
    val id: String,
    val name: String,
    val path: String,      // Relative path from root
    val type: ItemType,
    val mimeType: String,
    val size: Long,
    val exportMime: String? = null,
    val exportExtension: String? = null,
)

enum class ItemType { FILE, FOLDER }

// Google Docs export mappings
private val GOOGLE_DOCS_EXPORT = mapOf(
    "application/vnd.google-apps.document" to Pair("application/pdf", ".pdf"),
    "application/vnd.google-apps.spreadsheet" to Pair(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"
    ),
    "application/vnd.google-apps.presentation" to Pair(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"
    ),
    "application/vnd.google-apps.drawing" to Pair("image/png", ".png"),
)

// Non-exportable Google types
private val GOOGLE_SKIP_TYPES = setOf(
    "application/vnd.google-apps.form",
    "application/vnd.google-apps.map",
    "application/vnd.google-apps.site",
    "application/vnd.google-apps.shortcut",
)

// Invalid Windows/Android filename chars
private val INVALID_CHARS = Regex("[<>:\"/\\\\|?*]")

private val json = Json { ignoreUnknownKeys = true }

class DriveApiClient(
    private val apiKey: String = "",
    private val oauthToken: String = "",
    private val rateLimiter: RateLimiter,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://www.googleapis.com/drive/v3"

    companion object {
        /** Extract folder ID from a Google Drive URL */
        fun extractFolderId(urlOrId: String): String? {
            val trimmed = urlOrId.trim()
            // Pattern: /folders/ID
            Regex("/folders/([a-zA-Z0-9_-]+)").find(trimmed)?.let {
                return it.groupValues[1]
            }
            // Pattern: ?id=ID or &id=ID
            Regex("[?&]id=([a-zA-Z0-9_-]+)").find(trimmed)?.let {
                return it.groupValues[1]
            }
            // Direct ID
            if (trimmed.matches(Regex("^[a-zA-Z0-9_-]+$"))) return trimmed
            return null
        }

        /** Sanitize filename for Android filesystem */
        fun sanitizeFilename(name: String): String {
            return INVALID_CHARS.replace(name, "_").trim()
        }
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        val fullUrl = if (oauthToken.isBlank() && apiKey.isNotBlank()) {
            val sep = if (url.contains("?")) "&" else "?"
            "$url${sep}key=$apiKey"
        } else {
            url
        }

        val builder = Request.Builder().url(fullUrl)
        if (oauthToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $oauthToken")
        }
        return builder
    }

    /** Verify access to the folder */
    suspend fun verifyAccess(folderId: String): ApiResult<String> = withContext(Dispatchers.IO) {
        rateLimiter.waitForApiCall()
        val url = "$baseUrl/files/$folderId?fields=id,name,mimeType"
        val result = executeRequest(url)
        when (result) {
            is ApiResult.Success -> {
                val obj = json.parseToJsonElement(result.data).jsonObject
                val mimeType = obj["mimeType"]?.jsonPrimitive?.content ?: ""
                if (mimeType != "application/vnd.google-apps.folder") {
                    ApiResult.Error("El ID no es una carpeta (es: $mimeType)")
                } else {
                    val name = obj["name"]?.jsonPrimitive?.content ?: "Sin nombre"
                    ApiResult.Success(name)
                }
            }
            is ApiResult.Error -> result
            is ApiResult.QuotaExhausted -> result
        }
    }

    /** List all files in a folder (handles pagination) */
    suspend fun listFolder(folderId: String): ApiResult<List<JsonObject>> =
        withContext(Dispatchers.IO) {
            val allFiles = mutableListOf<JsonObject>()
            var pageToken: String? = null

            do {
                rateLimiter.waitForApiCall()
                val url = buildString {
                    append("$baseUrl/files")
                    append("?q='$folderId'+in+parents+and+trashed%3Dfalse")
                    append("&fields=nextPageToken,files(id,name,mimeType,size)")
                    append("&pageSize=100&orderBy=name")
                    pageToken?.let { append("&pageToken=$it") }
                }

                when (val result = executeRequest(url)) {
                    is ApiResult.Success -> {
                        val obj = json.parseToJsonElement(result.data).jsonObject
                        val files = obj["files"]?.jsonArray ?: return@withContext ApiResult.Success(
                            allFiles
                        )
                        files.forEach { allFiles.add(it.jsonObject) }
                        pageToken = obj["nextPageToken"]?.jsonPrimitive?.content
                    }
                    is ApiResult.Error -> return@withContext result
                    is ApiResult.QuotaExhausted -> return@withContext result
                }
            } while (pageToken != null)

            ApiResult.Success(allFiles)
        }

    /** Recursively list all files and folders */
    suspend fun listFolderRecursive(
        folderId: String,
        currentPath: String = "",
        onProgress: suspend (String) -> Unit = {},
    ): ApiResult<List<DriveItem>> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DriveItem>()
        onProgress(currentPath.ifEmpty { "(raíz)" })

        when (val result = listFolder(folderId)) {
            is ApiResult.Success -> {
                for (fileObj in result.data) {
                    val rawName = fileObj["name"]?.jsonPrimitive?.content ?: continue
                    val name = sanitizeFilename(rawName)
                    val id = fileObj["id"]?.jsonPrimitive?.content ?: continue
                    val mimeType = fileObj["mimeType"]?.jsonPrimitive?.content ?: ""
                    val relPath = if (currentPath.isEmpty()) name else "$currentPath/$name"

                    when {
                        mimeType == "application/vnd.google-apps.folder" -> {
                            items.add(
                                DriveItem(id, name, relPath, ItemType.FOLDER, mimeType, 0)
                            )
                            when (val subResult = listFolderRecursive(id, relPath, onProgress)) {
                                is ApiResult.Success -> items.addAll(subResult.data)
                                is ApiResult.Error -> return@withContext subResult
                                is ApiResult.QuotaExhausted -> return@withContext subResult
                            }
                        }
                        mimeType in GOOGLE_SKIP_TYPES -> { /* skip */ }
                        mimeType in GOOGLE_DOCS_EXPORT -> {
                            val (exportMime, ext) = GOOGLE_DOCS_EXPORT[mimeType]!!
                            items.add(
                                DriveItem(
                                    id, "$name$ext",
                                    if (currentPath.isEmpty()) "$name$ext" else "$currentPath/$name$ext",
                                    ItemType.FILE, mimeType, 0, exportMime, ext
                                )
                            )
                        }
                        else -> {
                            val size = fileObj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                            items.add(DriveItem(id, name, relPath, ItemType.FILE, mimeType, size))
                        }
                    }
                }
                ApiResult.Success(items)
            }
            is ApiResult.Error -> result
            is ApiResult.QuotaExhausted -> result
        }
    }

    /** Download a file from Drive to a local path using a .tmp cache file and Range resumption */
    suspend fun downloadFile(
        fileId: String,
        destFile: File,
        exportMime: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): ApiResult<Long> = withContext(Dispatchers.IO) {
        rateLimiter.waitForDownload()

        destFile.parentFile?.mkdirs()
        val tmpFile = File(destFile.parentFile, destFile.name + ".tmp")

        val url = if (exportMime != null) {
            "$baseUrl/files/$fileId/export?mimeType=$exportMime"
        } else {
            "$baseUrl/files/$fileId?alt=media"
        }

        for (attempt in 0 until rateLimiter.maxRetries) {
            try {
                val existingLength = if (tmpFile.exists() && exportMime == null) tmpFile.length() else 0L
                val requestBuilder = newRequestBuilder(url)
                if (existingLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$existingLength-")
                }
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful || response.code == 206) {
                    rateLimiter.reportSuccess()
                    val append = (response.code == 206 && existingLength > 0)
                    var totalBytes = if (append) existingLength else 0L
                    val responseLength = response.body?.contentLength() ?: 0L
                    val expectedTotalBytes = if (responseLength > 0) totalBytes + responseLength else 0L

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tmpFile, append).use { output ->
                            val buffer = ByteArray(32768)
                            var bytesRead: Int
                            var lastReportTime = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 150) {
                                    lastReportTime = now
                                    onProgress?.invoke(totalBytes, expectedTotalBytes)
                                }
                            }
                            onProgress?.invoke(totalBytes, expectedTotalBytes)
                        }
                    }
                    // Atomic rename from tmp to destination file
                    if (tmpFile.exists()) {
                        tmpFile.renameTo(destFile)
                    }
                    return@withContext ApiResult.Success(totalBytes)
                }

                if (response.code in listOf(403, 429)) {
                    val needsCooldown = rateLimiter.report403()
                    if (needsCooldown) {
                        return@withContext ApiResult.QuotaExhausted
                    }
                    val backoff = rateLimiter.getBackoffDelay(attempt)
                        ?: return@withContext ApiResult.QuotaExhausted
                    delay(backoff)
                    continue
                }

                response.close()
                return@withContext ApiResult.Error("HTTP ${response.code}")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e // Preserve coroutine cancellation
                }
                val backoff = rateLimiter.getBackoffDelay(attempt)
                if (backoff == null) {
                    return@withContext ApiResult.Error(e.message ?: "Error desconocido")
                }
                delay(backoff)
            }
        }
        ApiResult.QuotaExhausted
    }

    /** Execute an HTTP GET request with retry logic */
    private suspend fun executeRequest(url: String): ApiResult<String> {
        for (attempt in 0 until rateLimiter.maxRetries) {
            try {
                val request = newRequestBuilder(url).build()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                if (response.isSuccessful) {
                    rateLimiter.reportSuccess()
                    val body = response.body?.string() ?: ""
                    return ApiResult.Success(body)
                }
                if (response.code in listOf(403, 429)) {
                    val needsCooldown = rateLimiter.report403()
                    if (needsCooldown) return ApiResult.QuotaExhausted
                    val backoff = rateLimiter.getBackoffDelay(attempt) ?: return ApiResult.QuotaExhausted
                    delay(backoff)
                    continue
                }
                if (response.code == 404) return ApiResult.Error("No encontrado (404)")
                return ApiResult.Error("HTTP ${response.code}: ${response.body?.string()?.take(200)}")
            } catch (e: java.net.SocketTimeoutException) {
                val backoff = rateLimiter.getBackoffDelay(attempt) ?: return ApiResult.Error("Timeout")
                delay(backoff)
            } catch (e: java.io.IOException) {
                val backoff = rateLimiter.getBackoffDelay(attempt) ?: return ApiResult.Error("Error de conexión")
                delay(backoff)
            } catch (e: Exception) {
                return ApiResult.Error(e.message ?: "Error desconocido")
            }
        }
        return ApiResult.QuotaExhausted
    }
}
