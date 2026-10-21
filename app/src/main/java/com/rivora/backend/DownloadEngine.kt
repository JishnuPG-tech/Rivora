package com.rivora.backend

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rivora.data.DownloadItem
import com.rivora.data.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class ChunkStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class ChunkProgress(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    var downloadedBytes: Long = 0L,
    var status: ChunkStatus = ChunkStatus.PENDING,
    var networkType: NetworkAdapterType? = null
)

data class DownloadEngineState(
    val activeItem: DownloadItem? = null,
    val queue: List<DownloadItem> = emptyList(),
    val finished: List<DownloadItem> = emptyList(),
    val wifiSpeedMBps: Float = 0f,
    val cellularSpeedMBps: Float = 0f,
    val wifiStreams: Int = 0,
    val cellularStreams: Int = 0,
    val completedChunks: Int = 0,
    val totalChunks: Int = 200,
    val throughputHistory: List<Float> = List(60) { 0f },
    val isPaused: Boolean = false,
    val etaSeconds: Long = 0L,
    val isDownloading: Boolean = false,
    val error: String? = null
)

/**
 * Production-Grade Multi-Stream Chunk Download Engine.
 *
 * Features:
 * - Public Downloads folder integration (/sdcard/Download) visible to all Android File Managers.
 * - Multi-item queue: adding a file while downloading queues it instead of replacing.
 * - Dynamic deduplication: duplicate downloads get named "file (1).ext", "file (2).ext".
 * - Resumable segmented multi-stream downloads across Wi-Fi & Cellular.
 * - Atomic disk sync ensuring downloaded KB/MB are immediately reflected in File Manager.
 * - Status bar foreground notification service integration.
 */
class DownloadEngine private constructor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "DownloadEngine"
        const val DEFAULT_CHUNKS_COUNT = 200
        private const val BUFFER_SIZE = 32768 // 32 KB buffer

        @Volatile
        private var INSTANCE: DownloadEngine? = null

        fun getInstance(context: Context? = null): DownloadEngine {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context?.applicationContext 
                    ?: throw IllegalStateException("DownloadEngine must be initialized with a Context first")
                INSTANCE ?: DownloadEngine(appCtx).also { INSTANCE = it }
            }
        }
    }

    private val networkManager = MultiNetworkManager.getInstance(context)

    private val _engineState = MutableStateFlow(DownloadEngineState())
    val engineState: StateFlow<DownloadEngineState> = _engineState.asStateFlow()

    private var activeJob: Job? = null
    private var telemetryJob: Job? = null
    private var currentStorageTarget: StorageTarget? = null

    private val chunkList = mutableListOf<ChunkProgress>()
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val isCellularEnabled = AtomicBoolean(true)

    fun setCellularActive(active: Boolean) {
        isCellularEnabled.set(active)
    }

    // Byte counters for real speed calculation
    private val totalBytesDownloaded = AtomicLong(0L)
    private val wifiBytesTotal = AtomicLong(0L)
    private val cellularBytesTotal = AtomicLong(0L)

    private var lastWifiBytes = 0L
    private var lastCellularBytes = 0L
    private var lastTelemetryTime = System.currentTimeMillis()
    private var lastSyncTime = System.currentTimeMillis()

    init {
        startTelemetryLoop()
    }

    /**
     * Start or Queue a download.
     * If an active download is running, adds to queue rather than replacing!
     */
    fun startDownload(
        url: String,
        suggestedFileName: String? = null,
        totalBytes: Long = 0L,
        isResumable: Boolean = true,
        customTreeUri: Uri? = null,
        displayPath: String = "Downloads/"
    ) {
        scope.launch {
            try {
                // If metadata is incomplete, probe it first
                var finalTotalBytes = totalBytes
                var finalFileName = suggestedFileName
                var finalResumable = isResumable

                if (finalTotalBytes <= 0L || finalFileName == null) {
                    val probe = UrlProber.probe(url)
                    if (!probe.isDownloadable) {
                        Log.w("DownloadEngine", "Aborting startDownload: URL is not a downloadable file (${probe.validationWarning ?: probe.errorMessage})")
                        return@launch
                    }
                    if (probe.isSuccess) {
                        finalTotalBytes = probe.totalBytes
                        finalFileName = probe.fileName
                        finalResumable = probe.isResumable
                    } else {
                        finalFileName = finalFileName ?: url.substringAfterLast('/').substringBefore('?')
                        if (finalFileName.isBlank()) finalFileName = "file_${System.currentTimeMillis()}.bin"
                    }
                }

                // Collect currently active and queued filenames to prevent duplicates
                val activeNames = mutableListOf<String>()
                _engineState.value.activeItem?.let { activeNames.add(it.fileName) }
                _engineState.value.queue.forEach { activeNames.add(it.fileName) }
                _engineState.value.finished.forEach { activeNames.add(it.fileName) }

                // Resolve unique file name so it never replaces or overwrites an earlier download
                val uniqueFileName = DownloadStorageHelper.resolveUniqueFileName(
                    context = context,
                    baseName = finalFileName,
                    customTreeUri = customTreeUri,
                    activeNames = activeNames
                )

                val ext = uniqueFileName.substringAfterLast('.', "BIN").uppercase()
                val newItem = DownloadItem(
                    id = "dl-${System.currentTimeMillis()}-${(1000..9999).random()}",
                    fileName = uniqueFileName,
                    fileExtension = ext,
                    totalBytes = finalTotalBytes,
                    downloadedBytes = 0L,
                    status = DownloadStatus.QUEUED,
                    host = try { java.net.URI(url).host ?: "" } catch (_: Exception) { "" },
                    destinationPath = displayPath,
                    destinationUri = customTreeUri?.toString(),
                    downloadUrl = url
                )

                val currentState = _engineState.value
                val isCurrentlyActive = currentState.activeItem != null && 
                    currentState.activeItem.status == DownloadStatus.ACTIVE && 
                    currentState.isDownloading

                if (isCurrentlyActive) {
                    // Queue the download
                    _engineState.update { it.copy(queue = it.queue + newItem) }
                    Log.d(TAG, "Active download in progress; queued item: $uniqueFileName")
                } else {
                    // Start immediately
                    executeDownloadItem(newItem, finalResumable)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize download: ${e.message}", e)
                _engineState.update {
                    it.copy(
                        isDownloading = false,
                        error = e.message ?: "Failed to initialize download"
                    )
                }
            }
        }
    }

    /**
     * Executes the download for a specific item.
     */
    private suspend fun executeDownloadItem(item: DownloadItem, isResumable: Boolean) {
        val activeItem = item.copy(status = DownloadStatus.ACTIVE)
        _engineState.update {
            it.copy(
                activeItem = activeItem,
                isDownloading = true,
                isPaused = false,
                error = null,
                completedChunks = 0,
                totalChunks = DEFAULT_CHUNKS_COUNT
            )
        }

        // Start Foreground Notification Service
        try {
            DownloadNotificationService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start notification service: ${e.message}")
        }

        // Partition file into chunks
        partitionChunks(activeItem.totalBytes)

        // Allocate target file in Public Downloads or chosen custom folder
        val customUri = activeItem.destinationUri?.let { Uri.parse(it) }
        val target = DownloadStorageHelper.createDownloadTarget(
            context = context,
            fileName = activeItem.fileName,
            customTreeUri = customUri,
            totalBytes = activeItem.totalBytes
        )
        currentStorageTarget = target

        // Update item with file URI if created through MediaStore/SAF
        val updatedItem = activeItem.copy(destinationUri = target.uri?.toString() ?: activeItem.destinationUri)
        _engineState.update { it.copy(activeItem = updatedItem) }

        // Reset byte counters
        totalBytesDownloaded.set(0L)
        wifiBytesTotal.set(0L)
        cellularBytesTotal.set(0L)
        lastWifiBytes = 0L
        lastCellularBytes = 0L
        lastTelemetryTime = System.currentTimeMillis()
        lastSyncTime = System.currentTimeMillis()
        isPaused.set(false)
        isCancelled.set(false)

        // Launch concurrent download workers
        activeJob = scope.launch {
            runDownloadWorkers(activeItem.downloadUrl, activeItem.totalBytes, isResumable)
        }
    }

    private fun partitionChunks(totalBytes: Long) {
        chunkList.clear()
        if (totalBytes <= 0L) {
            chunkList.add(ChunkProgress(0, 0L, -1L))
            return
        }

        val numChunks = DEFAULT_CHUNKS_COUNT
        val chunkSize = (totalBytes + numChunks - 1) / numChunks

        for (i in 0 until numChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize - 1, totalBytes - 1)
            if (start <= end) {
                chunkList.add(ChunkProgress(index = i, startByte = start, endByte = end))
            }
        }
    }

    private suspend fun runDownloadWorkers(url: String, totalBytes: Long, isResumable: Boolean) {
        val channel = Channel<ChunkProgress>(Channel.UNLIMITED)
        chunkList.forEach { channel.trySend(it) }
        channel.close()

        val wifiClient = networkManager.getClient(NetworkAdapterType.WIFI)
        val cellularClient = networkManager.getClient(NetworkAdapterType.CELLULAR)

        val hasWifi = wifiClient != null
        val hasCellular = cellularClient != null

        val wifiWorkersCount = when {
            hasWifi && hasCellular -> 3
            hasWifi -> 5
            else -> 0
        }

        val cellWorkersCount = when {
            hasWifi && hasCellular -> 3
            hasCellular -> 5
            else -> 0
        }

        _engineState.update {
            it.copy(
                wifiStreams = wifiWorkersCount,
                cellularStreams = cellWorkersCount
            )
        }

        val workerJobs = mutableListOf<Job>()

        if (wifiClient != null && wifiWorkersCount > 0) {
            repeat(wifiWorkersCount) {
                val job = scope.launch {
                    for (chunk in channel) {
                        if (isCancelled.get()) break
                        while (isPaused.get()) delay(200)
                        downloadChunk(wifiClient, url, chunk, NetworkAdapterType.WIFI, isResumable)
                    }
                }
                workerJobs.add(job)
            }
        }

        if (cellularClient != null && cellWorkersCount > 0) {
            repeat(cellWorkersCount) {
                val job = scope.launch {
                    for (chunk in channel) {
                        if (isCancelled.get()) break
                        while (isPaused.get()) delay(200)
                        downloadChunk(cellularClient, url, chunk, NetworkAdapterType.CELLULAR, isResumable)
                    }
                }
                workerJobs.add(job)
            }
        }

        if (wifiWorkersCount == 0 && cellWorkersCount == 0) {
            val defaultClient = networkManager.getDefaultClient()
            _engineState.update { it.copy(cellularStreams = 4) }
            repeat(4) {
                val job = scope.launch {
                    for (chunk in channel) {
                        if (isCancelled.get()) break
                        while (isPaused.get()) delay(200)
                        downloadChunk(defaultClient, url, chunk, NetworkAdapterType.CELLULAR, isResumable)
                    }
                }
                workerJobs.add(job)
            }
        }

        // Await all workers
        workerJobs.forEach { it.join() }

        // Finalize completed download
        val target = currentStorageTarget
        if (!isCancelled.get() && target != null) {
            DownloadStorageHelper.markCompleted(context, target)
            currentStorageTarget = null

            _engineState.update { current ->
                val active = current.activeItem ?: return@update current
                val finishedItem = active.copy(
                    downloadedBytes = active.totalBytes,
                    status = DownloadStatus.FINISHED
                )
                current.copy(
                    isDownloading = false,
                    activeItem = finishedItem,
                    finished = listOf(finishedItem) + current.finished.filterNot { it.id == finishedItem.id },
                    completedChunks = current.totalChunks,
                    wifiSpeedMBps = 0f,
                    cellularSpeedMBps = 0f,
                    wifiStreams = 0,
                    cellularStreams = 0,
                    etaSeconds = 0
                )
            }
            Log.d(TAG, "Download finished successfully: ${target.displayName}")

            // Auto-start next queued download if available!
            checkAndStartNextQueued()
        }
    }

    private suspend fun downloadChunk(
        client: okhttp3.OkHttpClient,
        url: String,
        chunk: ChunkProgress,
        adapterType: NetworkAdapterType,
        isResumable: Boolean
    ) {
        chunk.status = ChunkStatus.DOWNLOADING
        chunk.networkType = adapterType

        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries && !isCancelled.get()) {
            try {
                val reqBuilder = Request.Builder().url(url)
                if (isResumable && chunk.endByte > 0) {
                    val currentStart = chunk.startByte + chunk.downloadedBytes
                    reqBuilder.header("Range", "bytes=$currentStart-${chunk.endByte}")
                }

                val response = client.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    response.close()
                    throw Exception("HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val stream = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get()) {
                        response.close()
                        return
                    }
                    while (isPaused.get()) delay(200)
                    while (!isCellularEnabled.get() && adapterType == NetworkAdapterType.CELLULAR && !isCancelled.get()) {
                        delay(200)
                    }

                    // Write to file at designated offset
                    val writeOffset = chunk.startByte + chunk.downloadedBytes
                    val target = currentStorageTarget
                    if (target != null) {
                        synchronized(target) {
                            target.sink.seek(writeOffset)
                            target.sink.write(buffer, 0, bytesRead)
                        }
                    }

                    chunk.downloadedBytes += bytesRead
                    totalBytesDownloaded.addAndGet(bytesRead.toLong())

                    if (adapterType == NetworkAdapterType.WIFI) {
                        wifiBytesTotal.addAndGet(bytesRead.toLong())
                    } else {
                        cellularBytesTotal.addAndGet(bytesRead.toLong())
                    }

                    // Periodic disk sync every 1 second so File Manager sees updated file size
                    val now = System.currentTimeMillis()
                    if (now - lastSyncTime > 1000) {
                        lastSyncTime = now
                        DownloadStorageHelper.sync(target)
                    }

                    // Update UI state
                    val currentTotal = totalBytesDownloaded.get()
                    _engineState.update { state ->
                        val item = state.activeItem ?: return@update state
                        state.copy(activeItem = item.copy(downloadedBytes = currentTotal))
                    }
                }

                response.close()
                chunk.status = ChunkStatus.COMPLETED

                val completed = chunkList.count { it.status == ChunkStatus.COMPLETED }
                _engineState.update { it.copy(completedChunks = completed) }
                return

            } catch (e: Exception) {
                if (e is CancellationException) return
                Log.w(TAG, "Chunk ${chunk.index} attempt $retryCount failed: ${e.message}")
                retryCount++
                delay(500)
            }
        }

        chunk.status = ChunkStatus.FAILED
    }

    /**
     * Checks if there are items waiting in the queue and starts the next one.
     */
    private fun checkAndStartNextQueued() {
        val nextItem = _engineState.value.queue.firstOrNull() ?: return
        _engineState.update { it.copy(queue = it.queue.drop(1)) }
        scope.launch {
            executeDownloadItem(nextItem, isResumable = true)
        }
    }

    /**
     * Start a queued item immediately.
     */
    fun startQueuedDownload(itemId: String) {
        val item = _engineState.value.queue.firstOrNull { it.id == itemId } ?: return
        // If an active download is running, put the current active download back into queue
        val currentActive = _engineState.value.activeItem
        stopActiveDownloadOnly()

        _engineState.update { current ->
            val newQueue = current.queue.filterNot { it.id == itemId }
            val updatedQueue = if (currentActive != null) listOf(currentActive.copy(status = DownloadStatus.QUEUED)) + newQueue else newQueue
            current.copy(queue = updatedQueue)
        }

        scope.launch {
            executeDownloadItem(item, isResumable = true)
        }
    }

    /**
     * Remove an item from the queue.
     */
    fun removeQueuedDownload(itemId: String) {
        _engineState.update { current ->
            current.copy(queue = current.queue.filterNot { it.id == itemId })
        }
    }

    /**
     * Pause the active download.
     */
    fun pauseDownload() {
        if (!isPaused.get()) {
            isPaused.set(true)
            _engineState.update { current ->
                val active = current.activeItem?.copy(status = DownloadStatus.PAUSED)
                current.copy(isPaused = true, activeItem = active)
            }
        }
    }

    /**
     * Resume a paused download.
     */
    fun resumeDownload() {
        if (isPaused.get()) {
            isPaused.set(false)
            _engineState.update { current ->
                val active = current.activeItem?.copy(status = DownloadStatus.ACTIVE)
                current.copy(isPaused = false, activeItem = active)
            }
        }
    }

    /**
     * Toggle pause / resume.
     */
    fun togglePause() {
        if (isPaused.get()) {
            resumeDownload()
        } else {
            pauseDownload()
        }
    }

    /**
     * Stop and cancel the active download, delete partially downloaded file from disk and MediaStore.
     */
    fun cancelDownload() {
        isCancelled.set(true)
        stopActiveDownloadOnly()

        // Delete the file cleanly from disk & MediaStore
        currentStorageTarget?.let { target ->
            DownloadStorageHelper.deleteTarget(context, target)
        }
        currentStorageTarget = null

        chunkList.clear()
        totalBytesDownloaded.set(0L)
        wifiBytesTotal.set(0L)
        cellularBytesTotal.set(0L)

        _engineState.update {
            it.copy(
                isDownloading = false,
                isPaused = false,
                wifiSpeedMBps = 0f,
                cellularSpeedMBps = 0f,
                wifiStreams = 0,
                cellularStreams = 0,
                activeItem = null,
                completedChunks = 0,
                totalChunks = 0,
                throughputHistory = List(60) { 0f },
                etaSeconds = 0
            )
        }

        // Stop notification service
        try {
            DownloadNotificationService.stop(context)
        } catch (_: Exception) {}

        // Auto-start next queued download if available
        checkAndStartNextQueued()
    }

    /**
     * Delete an item from queue or finished history, and remove its file.
     */
    fun deleteDownload(item: DownloadItem) {
        if (_engineState.value.activeItem?.id == item.id) {
            cancelDownload()
            return
        }
        _engineState.update { current ->
            current.copy(
                queue = current.queue.filterNot { it.id == item.id },
                finished = current.finished.filterNot { it.id == item.id }
            )
        }
        DownloadStorageHelper.deleteItemFile(context, item.fileName, item.destinationUri)
    }

    /**
     * Opens a finished downloaded file in an external app.
     */
    fun openDownloadedFile(item: DownloadItem) {
        DownloadStorageHelper.openFile(context, item.fileName, item.destinationUri)
    }

    private fun stopActiveDownloadOnly() {
        isCancelled.set(true)
        activeJob?.cancel()
        activeJob = null
        try {
            currentStorageTarget?.sink?.close()
        } catch (_: Exception) {}
    }

    /**
     * Periodic telemetry loop: calculates real MB/s transfer speed, updates throughput history.
     */
    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                delay(500)
                val now = System.currentTimeMillis()
                val dtSec = (now - lastTelemetryTime) / 1000f
                if (dtSec <= 0f) continue

                val currentWifi = wifiBytesTotal.get()
                val currentCellular = cellularBytesTotal.get()

                val wifiDelta = currentWifi - lastWifiBytes
                val cellDelta = currentCellular - lastCellularBytes

                lastWifiBytes = currentWifi
                lastCellularBytes = currentCellular
                lastTelemetryTime = now

                val isDownloading = _engineState.value.isDownloading
                val isPausedVal = isPaused.get()
                val wifiStreams = _engineState.value.wifiStreams
                val cellStreams = _engineState.value.cellularStreams

                val wifiSpeedMBps = if (isDownloading && !isPausedVal && wifiStreams > 0) {
                    ((wifiDelta / dtSec) / (1024f * 1024f)).coerceAtLeast(0f)
                } else 0f

                val cellSpeedMBps = if (isDownloading && !isPausedVal && cellStreams > 0) {
                    ((cellDelta / dtSec) / (1024f * 1024f)).coerceAtLeast(0f)
                } else 0f

                val totalSpeedMBps = wifiSpeedMBps + cellSpeedMBps

                _engineState.update { state ->
                    val updatedHistory = state.throughputHistory.drop(1) + totalSpeedMBps
                    val active = state.activeItem
                    val bytesPerSec = totalSpeedMBps * 1024f * 1024f
                    val etaSec = if (bytesPerSec > 1024f && active != null && active.totalBytes > 0) {
                        val remainingBytes = (active.totalBytes - active.downloadedBytes).coerceAtLeast(0L)
                        (remainingBytes / bytesPerSec).toLong()
                    } else 0L

                    state.copy(
                        wifiSpeedMBps = wifiSpeedMBps,
                        cellularSpeedMBps = cellSpeedMBps,
                        throughputHistory = updatedHistory,
                        etaSeconds = etaSec
                    )
                }
            }
        }
    }
}
