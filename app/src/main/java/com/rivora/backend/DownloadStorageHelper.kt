package com.rivora.backend

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Common random-access writing abstraction that works for both direct File and ParcelFileDescriptor.
 */
interface DownloadFileSink {
    fun seek(offset: Long)
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun setLength(length: Long)
    fun sync()
    fun close()
}

class RafFileSink(private val raf: RandomAccessFile) : DownloadFileSink {
    override fun seek(offset: Long) {
        raf.seek(offset)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        raf.write(buffer, offset, length)
    }

    override fun setLength(length: Long) {
        try { raf.setLength(length) } catch (_: Exception) {}
    }

    override fun sync() {
        try { raf.fd.sync() } catch (_: Exception) {}
    }

    override fun close() {
        try { raf.close() } catch (_: Exception) {}
    }
}

class PfdChannelSink(private val pfd: ParcelFileDescriptor) : DownloadFileSink {
    private val fos = FileOutputStream(pfd.fileDescriptor)
    private val channel: FileChannel = fos.channel

    override fun seek(offset: Long) {
        channel.position(offset)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        val byteBuf = ByteBuffer.wrap(buffer, offset, length)
        while (byteBuf.hasRemaining()) {
            channel.write(byteBuf)
        }
    }

    override fun setLength(length: Long) {
        try {
            if (length > 0) {
                channel.position(length - 1)
                channel.write(ByteBuffer.wrap(byteArrayOf(0)))
            }
        } catch (_: Exception) {}
    }

    override fun sync() {
        try { channel.force(false) } catch (_: Exception) {}
    }

    override fun close() {
        try { channel.close() } catch (_: Exception) {}
        try { fos.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }
}

/**
 * StorageTarget represents an open file or document ready for concurrent chunked random-access writing.
 */
data class StorageTarget(
    val uri: Uri?,
    val file: File?,
    val sink: DownloadFileSink,
    val displayName: String,
    val displayPath: String
)

/**
 * DownloadStorageHelper manages:
 * - Public Download directory integration visible directly in Android File Managers (/sdcard/Download).
 * - Custom SAF folder targets selected by the user.
 * - Dynamic unique filename deduplication ("file (1).ext", "file (2).ext").
 * - Random-access sparse allocation and atomic disk sync.
 * - MediaScanner indexing and external file opening.
 */
object DownloadStorageHelper {
    private const val TAG = "DownloadStorage"

    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }

    /**
     * Resolves a unique filename so that starting a new download never overwrites or replaces an existing one.
     */
    fun resolveUniqueFileName(
        context: Context,
        baseName: String,
        customTreeUri: Uri? = null,
        activeNames: List<String> = emptyList()
    ): String {
        val cleanName = baseName.trim().ifBlank { "download_${System.currentTimeMillis()}.bin" }
        val nameWithoutExt = cleanName.substringBeforeLast('.')
        val ext = if (cleanName.contains('.')) ".${cleanName.substringAfterLast('.')}" else ""

        var candidate = cleanName
        var counter = 1

        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val rivoraDir = RivoraPersistenceManager.getRivoraDir()

        while (true) {
            val inActiveList = activeNames.any { it.equals(candidate, ignoreCase = true) }
            val fileExistsOnDisk = try {
                File(rivoraDir, candidate).exists() || File(publicDir, candidate).exists()
            } catch (_: Exception) {
                false
            }

            if (!inActiveList && !fileExistsOnDisk) {
                return candidate
            }

            candidate = "$nameWithoutExt ($counter)$ext"
            counter++
        }
    }

    /**
     * Creates a StorageTarget in the public Downloads directory or custom chosen directory.
     * Ensures the file is immediately visible in Android's File Manager.
     */
    fun createDownloadTarget(
        context: Context,
        fileName: String,
        customTreeUri: Uri? = null,
        totalBytes: Long = 0L
    ): StorageTarget {
        val mimeType = getMimeType(fileName)

        // 1. If user specified a custom SAF directory via folder picker
        if (customTreeUri != null) {
            try {
                val docFile = DocumentFile.fromTreeUri(context, customTreeUri)
                val rivoraSubDir = docFile?.findFile(RivoraPersistenceManager.RIVORA_FOLDER_NAME)
                    ?: docFile?.createDirectory(RivoraPersistenceManager.RIVORA_FOLDER_NAME)
                val targetDir = rivoraSubDir ?: docFile
                val newFile = targetDir?.createFile(mimeType, fileName)
                if (newFile != null) {
                    val pfd = context.contentResolver.openFileDescriptor(newFile.uri, "rw")
                        ?: throw IllegalStateException("Could not open PFD for ${newFile.uri}")
                    val sink = PfdChannelSink(pfd)
                    if (totalBytes > 0L) {
                        sink.setLength(totalBytes)
                    }
                    val folderName = customTreeUri.lastPathSegment?.substringAfterLast(':') ?: "Custom"
                    return StorageTarget(
                        uri = newFile.uri,
                        file = null,
                        sink = sink,
                        displayName = fileName,
                        displayPath = "$folderName/Rivora/"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create in customTreeUri, falling back to persistent Rivora: ${e.message}")
            }
        }

        // 2. Direct public /sdcard/Download/Rivora file (dedicated app folder)
        try {
            val rivoraDir = RivoraPersistenceManager.getRivoraDir()
            val destFile = File(rivoraDir, fileName)
            if (rivoraDir.canWrite() || rivoraDir.exists()) {
                val raf = RandomAccessFile(destFile, "rw")
                val sink = RafFileSink(raf)
                if (totalBytes > 0L) {
                    sink.setLength(totalBytes)
                }
                // Notify system media scanner so it registers immediately
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
                return StorageTarget(
                    uri = null,
                    file = destFile,
                    sink = sink,
                    displayName = fileName,
                    displayPath = "Downloads/Rivora/"
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct public file write unpermitted, trying MediaStore: ${e.message}")
        }

        // 3. MediaStore.Downloads API (Standard Android 10+ scoped storage for public Downloads/Rivora)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/${RivoraPersistenceManager.RIVORA_FOLDER_NAME}")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                        ?: throw IllegalStateException("Could not open PFD for MediaStore uri: $uri")
                    val sink = PfdChannelSink(pfd)
                    if (totalBytes > 0L) {
                        sink.setLength(totalBytes)
                    }
                    return StorageTarget(
                        uri = uri,
                        file = null,
                        sink = sink,
                        displayName = fileName,
                        displayPath = "Downloads/Rivora/"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore insert failed: ${e.message}")
            }
        }

        // 4. Fallback: External app files dir
        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        fallbackDir.mkdirs()
        val fallbackFile = File(fallbackDir, fileName)
        val raf = RandomAccessFile(fallbackFile, "rw")
        val sink = RafFileSink(raf)
        if (totalBytes > 0L) {
            sink.setLength(totalBytes)
        }
        return StorageTarget(
            uri = null,
            file = fallbackFile,
            sink = sink,
            displayName = fileName,
            displayPath = "Downloads/"
        )
    }

    /**
     * Periodically syncs dirty pages to disk so File Managers see real-time size updates.
     */
    fun sync(target: StorageTarget?) {
        try {
            target?.sink?.sync()
        } catch (_: Exception) {}
    }

    /**
     * Marks the download as completed, publishes it in MediaStore, and triggers a full MediaScanner update.
     */
    fun markCompleted(context: Context, target: StorageTarget?) {
        if (target == null) return
        try {
            target.sink.close()
        } catch (_: Exception) {}

        // Release IS_PENDING on MediaStore
        if (target.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(target.uri, values, null, null)
            } catch (_: Exception) {}
        }

        // Trigger MediaScanner for direct files
        target.file?.let { file ->
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(getMimeType(target.displayName)),
                    null
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Deletes the target file completely from disk and MediaStore when cancelled, and removes its backup state.
     */
    fun deleteTarget(context: Context, target: StorageTarget?) {
        if (target == null) return
        try {
            target.sink.close()
        } catch (_: Exception) {}

        // Remove persistent backup metadata
        RivoraPersistenceManager.deleteDownloadState(target.displayName, target.file)

        target.file?.let { file ->
            try {
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }

        target.uri?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
    }

    /**
     * Deletes a file by item representation and clears its backup metadata.
     */
    fun deleteItemFile(context: Context, fileName: String, fileUriString: String?) {
        // Remove persistent backup metadata
        RivoraPersistenceManager.deleteDownloadState(fileName)

        if (!fileUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(fileUriString)
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
        try {
            val rivoraDir = RivoraPersistenceManager.getRivoraDir()
            val file = File(rivoraDir, fileName)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(publicDir, fileName)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(extDir, fileName)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    /**
     * Opens the completed download in the user's preferred viewer/app.
     */
    fun openFile(context: Context, fileName: String, fileUriString: String?) {
        try {
            val mimeType = getMimeType(fileName)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (!fileUriString.isNullOrBlank()) {
                intent.setDataAndType(Uri.parse(fileUriString), mimeType)
                context.startActivity(intent)
                return
            }

            val rivoraFile = File(RivoraPersistenceManager.getRivoraDir(), fileName)
            val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            val fileToOpen = when {
                rivoraFile.exists() -> rivoraFile
                publicFile.exists() -> publicFile
                else -> File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            }

            if (fileToOpen.exists()) {
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToOpen)
                intent.setDataAndType(contentUri, mimeType)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file: ${e.message}")
        }
    }
}
