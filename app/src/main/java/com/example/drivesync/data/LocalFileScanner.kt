package com.example.drivesync.data

import java.io.File

/** Represents a local file or folder with its relative path */
data class LocalItem(
    val relativePath: String,  // Forward-slash separated
    val type: ItemType,
    val size: Long,
)

class LocalFileScanner(private val baseDir: File) {

    /** Scan the local directory and return a set of relative paths (lowercase for comparison) */
    fun scan(): Map<String, LocalItem> {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
            return emptyMap()
        }

        val items = mutableMapOf<String, LocalItem>()

        baseDir.walkTopDown().forEach { file ->
            if (file == baseDir) return@forEach
            val relativePath = file.relativeTo(baseDir).path.replace("\\", "/")
            val type = if (file.isDirectory) ItemType.FOLDER else ItemType.FILE
            val size = if (file.isFile) file.length() else 0L
            items[relativePath] = LocalItem(relativePath, type, size)
        }

        return items
    }

    /** Get stats about the local directory */
    fun getStats(): LocalStats {
        val items = scan()
        val folders = items.values.count { it.type == ItemType.FOLDER }
        val files = items.values.count { it.type == ItemType.FILE }
        val totalSize = items.values.filter { it.type == ItemType.FILE }.sumOf { it.size }
        return LocalStats(folders, files, totalSize)
    }

    data class LocalStats(val folders: Int, val files: Int, val totalSizeBytes: Long)
}
