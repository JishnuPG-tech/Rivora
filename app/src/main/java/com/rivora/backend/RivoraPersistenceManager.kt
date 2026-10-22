package com.rivora.backend

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.rivora.data.DownloadItem
import com.rivora.data.DownloadStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persistent storage and automatic state backup/restore for Rivora downloads.
 *
 * Architecture:
 * - Creates and manages a dedicated 'Rivora' directory in public storage (e.g. /sdcard/Download/Rivora).
 * - Persists detailed download metadata and chunk state in a dedicated '.rivora' directory.
 * - Survives complete APK uninstallation and reinstallation:
 *   When the APK is deleted and reinstalled, Rivora scans the dedicated folder on launch,
 *   restores all in-progress downloads in PAUSED state, and allows resumption without restarting from byte 0.
 */
object RivoraPersistenceManager {
    private const val TAG = "RivoraPersistence"
    const val RIVORA_FOLDER_NAME = "Rivora"
    private const val META_FOLDER_NAME = ".rivora"

    /**
     * Returns the primary persistent Rivora directory (e.g. /storage/emulated/0/Download/Rivora).
     */
    fun getRivoraDir(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val rivoraDir = File(publicDownloads, RIVORA_FOLDER_NAME)
        if (!rivoraDir.exists()) {
            rivoraDir.mkdirs()
        }
        return rivoraDir
    }

    /**
     * Returns the metadata backup directory (e.g. /storage/emulated/0/Download/Rivora/.rivora).
     */
    fun getMetaDir(baseDir: File = getRivoraDir()): File {
        val metaDir = File(baseDir, META_FOLDER_NAME)
        if (!metaDir.exists()) {
            metaDir.mkdirs()
        }
        val noMedia = File(metaDir, ".nomedia")
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }
        return metaDir
    }

    /**
     * Persists download item metadata and chunk progress to disk in the dedicated Rivora directory.
     */
    fun saveDownloadState(
        item: DownloadItem,
        chunks: List<ChunkProgress>,
        targetFile: File? = null
    ) {
        try {
            val dir = targetFile?.parentFile ?: getRivoraDir()
            val metaDir = getMetaDir(dir)
            val metaFile = File(metaDir, "${item.fileName}.meta.json")

            val json = JSONObject().apply {
                put("id", item.id)
                put("url", item.downloadUrl)
                put("fileName", item.fileName)
                put("fileExtension", item.fileExtension)
                put("totalBytes", item.totalBytes)
                put("downloadedBytes", item.downloadedBytes)
                put("status", item.status.name)
                put("destinationPath", item.destinationPath)
                put("destinationUri", item.destinationUri)
                put("filePath", targetFile?.absolutePath ?: File(dir, item.fileName).absolutePath)
                put("updatedAt", System.currentTimeMillis())

                val chunksArr = JSONArray()
                chunks.forEach { c ->
                    val cJson = JSONObject().apply {
                        put("index", c.index)
                        put("startByte", c.startByte)
                        put("endByte", c.endByte)
                        put("downloadedBytes", c.downloadedBytes)
                        put("status", c.status.name)
                    }
                    chunksArr.put(cJson)
                }
                put("chunks", chunksArr)
            }

            // Atomic file write using temporary file
            val tempFile = File(metaDir, "${item.fileName}.meta.json.tmp")
            tempFile.writeText(json.toString(2))
            if (tempFile.exists()) {
                if (metaFile.exists()) metaFile.delete()
                tempFile.renameTo(metaFile)
            }
            Log.d(TAG, "Persisted backup for ${item.fileName} (${item.downloadedBytes}/${item.totalBytes} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist backup for ${item.fileName}: ${e.message}", e)
        }
    }

    /**
     * Loads saved chunk progress for a specific file from disk.
     */
    fun loadChunks(fileName: String, targetFile: File? = null): List<ChunkProgress> {
        try {
            val dir = targetFile?.parentFile ?: getRivoraDir()
            val metaDir = getMetaDir(dir)
            val metaFile = File(metaDir, "$fileName.meta.json")
            if (!metaFile.exists()) return emptyList()

            val text = metaFile.readText()
            val json = JSONObject(text)
            val chunksArr = json.optJSONArray("chunks") ?: return emptyList()

            val chunks = mutableListOf<ChunkProgress>()
            for (i in 0 until chunksArr.length()) {
                val cJson = chunksArr.getJSONObject(i)
                val cStatusStr = cJson.optString("status", ChunkStatus.PENDING.name)
                val cStatus = try { ChunkStatus.valueOf(cStatusStr) } catch (_: Exception) { ChunkStatus.PENDING }
                chunks.add(
                    ChunkProgress(
                        index = cJson.getInt("index"),
                        startByte = cJson.getLong("startByte"),
                        endByte = cJson.getLong("endByte"),
                        downloadedBytes = cJson.getLong("downloadedBytes"),
                        status = if (cStatus == ChunkStatus.COMPLETED) ChunkStatus.COMPLETED else ChunkStatus.PENDING
                    )
                )
            }
            return chunks
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chunks for $fileName: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Restored session containing all restored active, queued, and finished downloads.
     */
    data class RestoredSession(
        val activeItem: DownloadItem?,
        val activeChunks: List<ChunkProgress>,
        val queuedItems: List<DownloadItem>,
        val finishedItems: List<DownloadItem>
    )

    /**
     * Restores all download states from the persistent Rivora directory.
     * Invoked automatically when Rivora starts up.
     */
    fun restoreDownloads(context: Context): RestoredSession {
        val rivoraDir = getRivoraDir()
        val metaDir = getMetaDir(rivoraDir)

        val metaFiles = metaDir.listFiles { f -> f.extension == "json" && !f.name.endsWith(".tmp") }
            ?: emptyArray()

        val unfinishedItems = mutableListOf<Pair<DownloadItem, List<ChunkProgress>>>()
        val finishedItems = mutableListOf<DownloadItem>()

        for (metaFile in metaFiles) {
            try {
                val text = metaFile.readText()
                val json = JSONObject(text)

                val fileName = json.getString("fileName")
                val filePath = json.optString("filePath", File(rivoraDir, fileName).absolutePath)
                val targetFile = File(filePath)
                val defaultFile = File(rivoraDir, fileName)

                val statusStr = json.optString("status", DownloadStatus.PAUSED.name)
                val status = try { DownloadStatus.valueOf(statusStr) } catch (_: Exception) { DownloadStatus.PAUSED }
                val downloadedBytes = json.getLong("downloadedBytes").coerceAtLeast(0L)
                val isQueued = status == DownloadStatus.QUEUED || (status == DownloadStatus.PAUSED && downloadedBytes == 0L)

                // Verify the physical payload file exists on disk (unless item is queued and not yet allocated)
                if (!isQueued && !targetFile.exists() && !defaultFile.exists()) {
                    Log.w(TAG, "Payload file $fileName does not exist on disk, skipping restore")
                    continue
                }

                val totalBytes = json.getLong("totalBytes")
                val url = json.optString("url", "")
                val id = json.optString("id", "dl_${System.currentTimeMillis()}")
                val ext = json.optString("fileExtension", fileName.substringAfterLast('.', "BIN").uppercase())
                val destPath = json.optString("destinationPath", "Downloads/Rivora/")

                val chunksArr = json.optJSONArray("chunks")
                val chunks = mutableListOf<ChunkProgress>()
                if (chunksArr != null) {
                    for (i in 0 until chunksArr.length()) {
                        val cJson = chunksArr.getJSONObject(i)
                        val cStatusStr = cJson.optString("status", ChunkStatus.PENDING.name)
                        val cStatus = try { ChunkStatus.valueOf(cStatusStr) } catch (_: Exception) { ChunkStatus.PENDING }
                        chunks.add(
                            ChunkProgress(
                                index = cJson.getInt("index"),
                                startByte = cJson.getLong("startByte"),
                                endByte = cJson.getLong("endByte"),
                                downloadedBytes = cJson.getLong("downloadedBytes"),
                                status = if (cStatus == ChunkStatus.COMPLETED) ChunkStatus.COMPLETED else ChunkStatus.PENDING
                            )
                        )
                    }
                }

                if (status == DownloadStatus.FINISHED || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                    finishedItems.add(
                        DownloadItem(
                            id = id,
                            fileName = fileName,
                            fileExtension = ext,
                            totalBytes = totalBytes,
                            downloadedBytes = totalBytes,
                            status = DownloadStatus.FINISHED,
                            destinationPath = destPath,
                            downloadUrl = url
                        )
                    )
                } else {
                    // Restore unfinished downloads in PAUSED or QUEUED state
                    val restoredItem = DownloadItem(
                        id = id,
                        fileName = fileName,
                        fileExtension = ext,
                        totalBytes = totalBytes,
                        downloadedBytes = downloadedBytes,
                        status = if (status == DownloadStatus.QUEUED) DownloadStatus.QUEUED else DownloadStatus.PAUSED,
                        destinationPath = destPath,
                        downloadUrl = url
                    )
                    unfinishedItems.add(restoredItem to chunks)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring metadata from ${metaFile.name}: ${e.message}", e)
            }
        }

        // Deduplicate unfinished downloads by URL or identical filename:
        // Keep the one with highest progress and delete stale duplicate metadata files
        val deduplicatedUnfinished = mutableListOf<Pair<DownloadItem, List<ChunkProgress>>>()
        val seenUrls = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()

        // Sort descending by downloaded bytes so best progress wins
        unfinishedItems.sortByDescending { it.first.downloadedBytes }

        for (pair in unfinishedItems) {
            val item = pair.first
            val url = item.downloadUrl
            val cleanName = item.fileName.replace(Regex(""" \(\d+\)\."""), ".")

            val isDuplicateUrl = url.isNotBlank() && seenUrls.contains(url)
            val isDuplicateName = seenNames.contains(cleanName)

            if (isDuplicateUrl || isDuplicateName) {
                Log.d(TAG, "Deduplicating redundant download: ${item.fileName}, removing stale meta")
                deleteDownloadState(item.fileName)
            } else {
                if (url.isNotBlank()) seenUrls.add(url)
                seenNames.add(cleanName)
                deduplicatedUnfinished.add(pair)
            }
        }

        val activePair = deduplicatedUnfinished.firstOrNull()
        val queuedItems = deduplicatedUnfinished.drop(1).map { it.first }

        return RestoredSession(
            activeItem = activePair?.first,
            activeChunks = activePair?.second ?: emptyList(),
            queuedItems = queuedItems,
            finishedItems = finishedItems
        )
    }

    /**
     * Deletes the persistent metadata backup when a download is canceled or removed by the user.
     */
    fun deleteDownloadState(fileName: String, targetFile: File? = null) {
        try {
            val dir = targetFile?.parentFile ?: getRivoraDir()
            val metaDir = getMetaDir(dir)
            val metaFile = File(metaDir, "$fileName.meta.json")
            if (metaFile.exists()) metaFile.delete()
            val tmpFile = File(metaDir, "$fileName.meta.json.tmp")
            if (tmpFile.exists()) tmpFile.delete()
            Log.d(TAG, "Deleted backup state for $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete backup state for $fileName: ${e.message}", e)
        }
    }
}
