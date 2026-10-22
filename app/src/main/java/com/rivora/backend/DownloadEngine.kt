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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
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

    @Volatile
    private var smoothedWifiSpeedMBps: Float = 0f
    @Volatile
    private var smoothedCellSpeedMBps: Float = 0f

    init {
        restorePersistedDownloads()
        startTelemetryLoop()
    }

    /**
     * Scans the persistent storage directory (/sdcard/Download/Rivora) and restores all saved
     * downloads in PAUSED state. Survives APK uninstallation and reinstallation!
     */
    private fun restorePersistedDownloads() {
        try {
            val session = RivoraPersistenceManager.restoreDownloads(context)
            if (session.activeItem != null) {
                chunkList.clear()
                chunkList.addAll(session.activeChunks)
                totalBytesDownloaded.set(session.activeItem.downloadedBytes)

                val completedCount = session.activeChunks.count { it.status == ChunkStatus.COMPLETED }
                _engineState.update { current ->
                    current.copy(
                        activeItem = session.activeItem,
                        queue = session.queuedItems,
                        finished = session.finishedItems,
                        isPaused = true,
                        isDownloading = false,
                        completedChunks = completedCount,
                        totalChunks = if (session.activeChunks.isNotEmpty()) session.activeChunks.size else current.totalChunks
                    )
                }
                Log.d(TAG, "Restored active download from backup: ${session.activeItem.fileName} (${session.activeItem.downloadedBytes}/${session.activeItem.totalBytes} bytes)")
            } else if (session.queuedItems.isNotEmpty() || session.finishedItems.isNotEmpty()) {
                _engineState.update { current ->
                    current.copy(
                        queue = session.queuedItems,
                        finished = session.finishedItems
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore persisted downloads: ${e.message}", e)
        }
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
        displayPath: String = "Downloads/Rivora/"
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

                // Check if this item is ALREADY active or queued to prevent duplicates!
                val existingActive = _engineState.value.activeItem
                if (existingActive != null && (existingActive.downloadUrl == url || existingActive.fileName == finalFileName)) {
                    Log.d(TAG, "Item already active, resuming: ${existingActive.fileName}")
                    if (isPaused.get()) {
                        resumeDownload()
                    }
                    return@launch
                }

                val existingQueued = _engineState.value.queue.firstOrNull { it.downloadUrl == url || it.fileName == finalFileName }
                if (existingQueued != null) {
                    Log.d(TAG, "Item already in queue: ${existingQueued.fileName}")
                    return@launch
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
                val hasActiveOrPaused = currentState.activeItem != null && 
                    (currentState.activeItem.status == DownloadStatus.ACTIVE || currentState.activeItem.status == DownloadStatus.PAUSED)

                if (hasActiveOrPaused) {
                    // Queue the download and persist its queue entry
                    _engineState.update { it.copy(queue = it.queue + newItem) }
                    RivoraPersistenceManager.saveDownloadState(newItem, emptyList())
                    Log.d(TAG, "Active/paused download in progress; queued item: $uniqueFileName")
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

        // ALWAYS clear previous in-memory chunk list so chunks from previous downloads NEVER leak
        chunkList.clear()

        // Always check persistent storage for saved chunks of this specific file
        val savedChunks = RivoraPersistenceManager.loadChunks(activeItem.fileName)
        if (savedChunks.isNotEmpty() && savedChunks.any { it.downloadedBytes > 0L }) {
            chunkList.addAll(savedChunks)
            totalBytesDownloaded.set(savedChunks.sumOf { it.downloadedBytes })
            Log.d(TAG, "Restored ${savedChunks.size} chunks for ${activeItem.fileName} (${totalBytesDownloaded.get()} bytes)")
        } else {
            partitionChunks(activeItem.totalBytes)
            totalBytesDownloaded.set(0L)
            Log.d(TAG, "Partitioned fresh chunks for ${activeItem.fileName} (total: ${activeItem.totalBytes} bytes)")
        }

        val completedCount = chunkList.count { it.status == ChunkStatus.COMPLETED }

        _engineState.update {
            it.copy(
                activeItem = activeItem,
                isDownloading = true,
                isPaused = false,
                error = null,
                completedChunks = completedCount,
                totalChunks = chunkList.size.coerceAtLeast(DEFAULT_CHUNKS_COUNT)
            )
        }

        // Start Foreground Notification Service
        try {
            DownloadNotificationService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start notification service: ${e.message}")
        }

        // Allocate target file in Public Downloads/Rivora or chosen custom folder
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

        // Save initial state to backup folder immediately
        RivoraPersistenceManager.saveDownloadState(updatedItem, chunkList, target.file)

        // Reset telemetry byte counters
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
        // Dynamic Concurrent Linked Queue for all pending chunks
        val pendingChunks = ConcurrentLinkedQueue<ChunkProgress>()
        chunkList.forEach { chunk ->
            val chunkLen = if (chunk.endByte > 0) (chunk.endByte - chunk.startByte + 1) else -1L
            if (chunk.status != ChunkStatus.COMPLETED && (chunkLen <= 0L || chunk.downloadedBytes < chunkLen)) {
                chunk.status = ChunkStatus.PENDING
                pendingChunks.add(chunk)
            }
        }

        val activeWorkers = ConcurrentHashMap<NetworkAdapterType, MutableList<Job>>()

        fun cancelWorkersForAdapter(type: NetworkAdapterType) {
            val jobList = activeWorkers[type] ?: return
            synchronized(jobList) {
                jobList.forEach { it.cancel() }
                jobList.clear()
            }
        }

        fun ensureWorkersForAdapter(type: NetworkAdapterType, desiredStreams: Int) {
            if (desiredStreams <= 0) {
                cancelWorkersForAdapter(type)
                return
            }

            val jobList = activeWorkers.getOrPut(type) { mutableListOf() }
            synchronized(jobList) {
                jobList.removeAll { it.isCompleted }
                val needed = desiredStreams - jobList.size
                if (needed <= 0) return

                repeat(needed) {
                    val job = scope.launch {
                        while (!isCancelled.get() && pendingChunks.isNotEmpty()) {
                            while (isPaused.get() && !isCancelled.get()) delay(200)
                            while (!isCellularEnabled.get() && type == NetworkAdapterType.CELLULAR && !isCancelled.get()) delay(200)
                            if (isCancelled.get()) break

                            // Wait gracefully if device temporarily loses connectivity (e.g. Aeroplane mode)
                            while (!networkManager.isAnyNetworkConnected() && !isCancelled.get()) {
                                delay(500)
                            }

                            val chunk = pendingChunks.poll() ?: break
                            // Fetch dynamic, current client bound to latest network socket
                            val dynamicClient = if (type == NetworkAdapterType.UNKNOWN) {
                                networkManager.getDefaultClient()
                            } else {
                                networkManager.getClient(type) ?: networkManager.getDefaultClient()
                            }

                            val success = downloadChunk(dynamicClient, url, chunk, type, isResumable)
                            if (!success && chunk.status != ChunkStatus.COMPLETED && !isCancelled.get()) {
                                // Put back on queue for another active stream or retry
                                pendingChunks.add(chunk)
                                delay(300)
                            }
                        }
                    }
                    jobList.add(job)
                }
            }
        }

        // Active Monitor: Continuously adapt streams as networks connect/disconnect
        val monitorJob = scope.launch {
            networkManager.networkStates.collect { states ->
                val wifiConnected = states[NetworkAdapterType.WIFI]?.isConnected == true
                val cellConnected = states[NetworkAdapterType.CELLULAR]?.isConnected == true && isCellularEnabled.get()

                val (targetWifiStreams, targetCellStreams) = when {
                    wifiConnected && cellConnected -> 3 to 3
                    wifiConnected -> 5 to 0
                    cellConnected -> 0 to 5
                    else -> 0 to 0
                }

                _engineState.update {
                    it.copy(
                        wifiStreams = targetWifiStreams,
                        cellularStreams = targetCellStreams
                    )
                }

                if (wifiConnected && targetWifiStreams > 0) {
                    ensureWorkersForAdapter(NetworkAdapterType.WIFI, targetWifiStreams)
                } else if (!wifiConnected) {
                    cancelWorkersForAdapter(NetworkAdapterType.WIFI)
                }

                if (cellConnected && targetCellStreams > 0) {
                    ensureWorkersForAdapter(NetworkAdapterType.CELLULAR, targetCellStreams)
                } else if (!cellConnected) {
                    cancelWorkersForAdapter(NetworkAdapterType.CELLULAR)
                }

                if (!wifiConnected && !cellConnected) {
                    ensureWorkersForAdapter(NetworkAdapterType.UNKNOWN, 4)
                }
            }
        }

        // Wait until all chunks are completed or cancelled
        while (!isCancelled.get() && chunkList.any { it.status != ChunkStatus.COMPLETED }) {
            delay(200)
        }

        monitorJob.cancel()
        activeWorkers.values.forEach { list ->
            synchronized(list) {
                list.forEach { it.cancel() }
                list.clear()
            }
        }

        // Finalize completed download
        val target = currentStorageTarget
        if (!isCancelled.get() && target != null) {
            DownloadStorageHelper.markCompleted(context, target)
            currentStorageTarget = null

            val active = _engineState.value.activeItem
            if (active != null) {
                val finishedItem = active.copy(
                    downloadedBytes = active.totalBytes,
                    status = DownloadStatus.FINISHED
                )
                RivoraPersistenceManager.saveDownloadState(finishedItem, chunkList, target.file)
            }

            smoothedWifiSpeedMBps = 0f
            smoothedCellSpeedMBps = 0f
            _engineState.update { current ->
                val activeItem = current.activeItem ?: return@update current
                val finishedItem = activeItem.copy(
                    downloadedBytes = activeItem.totalBytes,
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

            // Cleanly clear completed chunk state before queue transition
            chunkList.clear()
            totalBytesDownloaded.set(0L)
            wifiBytesTotal.set(0L)
            cellularBytesTotal.set(0L)

            // Auto-start next queued download if available!
            checkAndStartNextQueued()
        }
    }

    private suspend fun downloadChunk(
        initialClient: okhttp3.OkHttpClient,
        url: String,
        chunk: ChunkProgress,
        adapterType: NetworkAdapterType,
        isResumable: Boolean
    ): Boolean {
        chunk.status = ChunkStatus.DOWNLOADING
        chunk.networkType = adapterType

        var retryCount = 0
        val maxRetries = 5

        while (retryCount < maxRetries && !isCancelled.get()) {
            try {
                // Ensure active client dynamically reflects the currently bound physical network
                val client = if (retryCount == 0) {
                    initialClient
                } else if (adapterType == NetworkAdapterType.UNKNOWN) {
                    networkManager.getDefaultClient()
                } else {
                    networkManager.getClient(adapterType) ?: networkManager.getDefaultClient()
                }

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
                        return false
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

                    // Periodic disk sync and state persistence every 1 second
                    val now = System.currentTimeMillis()
                    if (now - lastSyncTime > 1000) {
                        lastSyncTime = now
                        DownloadStorageHelper.sync(target)
                        val active = _engineState.value.activeItem
                        if (active != null) {
                            RivoraPersistenceManager.saveDownloadState(
                                item = active.copy(downloadedBytes = totalBytesDownloaded.get(), status = DownloadStatus.ACTIVE),
                                chunks = chunkList,
                                targetFile = target?.file
                            )
                        }
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
                return true

            } catch (e: Exception) {
                if (e is CancellationException) return false
                Log.w(TAG, "Chunk ${chunk.index} attempt $retryCount failed on $adapterType: ${e.message}")
                if (!networkManager.isAnyNetworkConnected()) {
                    Log.d(TAG, "Network offline during chunk ${chunk.index}, pausing until connection restores...")
                    while (!networkManager.isAnyNetworkConnected() && !isCancelled.get()) {
                        delay(500)
                    }
                    Log.d(TAG, "Network restored, resuming chunk ${chunk.index}")
                } else {
                    retryCount++
                    delay(300)
                }
            }
        }

        chunk.status = ChunkStatus.PENDING
        return false
    }

    /**
     * Checks if there are items waiting in the queue and starts the next one.
     */
    private fun checkAndStartNextQueued() {
        val nextItem = _engineState.value.queue.firstOrNull() ?: run {
            try {
                DownloadNotificationService.stop(context)
            } catch (_: Exception) {}
            return
        }
        _engineState.update { it.copy(queue = it.queue.drop(1)) }
        scope.launch {
            delay(300)
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
        if (currentActive != null && currentActive.status != DownloadStatus.FINISHED) {
            RivoraPersistenceManager.saveDownloadState(
                item = currentActive.copy(status = DownloadStatus.QUEUED, downloadedBytes = totalBytesDownloaded.get()),
                chunks = chunkList,
                targetFile = currentStorageTarget?.file
            )
        }
        stopActiveDownloadOnly()

        _engineState.update { current ->
            val newQueue = current.queue.filterNot { it.id == itemId }
            val updatedQueue = if (currentActive != null && currentActive.status != DownloadStatus.FINISHED) {
                listOf(currentActive.copy(status = DownloadStatus.QUEUED)) + newQueue
            } else newQueue
            current.copy(queue = updatedQueue)
        }

        scope.launch {
            delay(200)
            executeDownloadItem(item, isResumable = true)
        }
    }

    /**
     * Remove an item from the queue.
     */
    fun removeQueuedDownload(itemId: String) {
        val item = _engineState.value.queue.firstOrNull { it.id == itemId }
        if (item != null) {
            RivoraPersistenceManager.deleteDownloadState(item.fileName)
        }
        _engineState.update { current ->
            current.copy(queue = current.queue.filterNot { it.id == itemId })
        }
    }

    /**
     * Pause the active download and save its persistent backup state immediately.
     */
    fun pauseDownload() {
        if (!isPaused.get()) {
            isPaused.set(true)
            smoothedWifiSpeedMBps = 0f
            smoothedCellSpeedMBps = 0f
            _engineState.update { current ->
                val active = current.activeItem?.copy(status = DownloadStatus.PAUSED)
                current.copy(isPaused = true, activeItem = active)
            }
            val active = _engineState.value.activeItem
            if (active != null) {
                RivoraPersistenceManager.saveDownloadState(
                    item = active.copy(status = DownloadStatus.PAUSED, downloadedBytes = totalBytesDownloaded.get()),
                    chunks = chunkList,
                    targetFile = currentStorageTarget?.file
                )
            }
        }
    }

    /**
     * Resume a paused download. If the background job is not running (e.g. after app restart/reinstall),
     * spawns workers to resume right where the download left off without starting from byte 0.
     */
    fun resumeDownload() {
        val active = _engineState.value.activeItem ?: return
        isPaused.set(false)
        isCancelled.set(false)
        smoothedWifiSpeedMBps = 0f
        smoothedCellSpeedMBps = 0f
        _engineState.update { current ->
            val updated = current.activeItem?.copy(status = DownloadStatus.ACTIVE)
            current.copy(isPaused = false, isDownloading = true, activeItem = updated)
        }

        if (activeJob == null || activeJob?.isActive != true) {
            scope.launch {
                executeDownloadItem(active, isResumable = true)
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
     * Stop and cancel the active download, delete partially downloaded file from disk and MediaStore,
     * and delete its persistent backup state.
     */
    fun cancelDownload() {
        isCancelled.set(true)
        stopActiveDownloadOnly()

        val active = _engineState.value.activeItem
        if (active != null) {
            RivoraPersistenceManager.deleteDownloadState(active.fileName, currentStorageTarget?.file)
        }

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
     * Delete an item from queue or finished history, remove its file, and delete its backup state.
     */
    fun deleteDownload(item: DownloadItem) {
        RivoraPersistenceManager.deleteDownloadState(item.fileName)
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

                if (isDownloading && !isPausedVal) {
                    val rawWifi = ((wifiDelta / dtSec) / (1024f * 1024f)).coerceAtLeast(0f)
                    val rawCell = ((cellDelta / dtSec) / (1024f * 1024f)).coerceAtLeast(0f)

                    // Exponential Moving Average (EMA) with alpha = 0.40f to eliminate 0.0 drops between TCP packet bursts
                    smoothedWifiSpeedMBps = if (smoothedWifiSpeedMBps <= 0.001f) {
                        rawWifi
                    } else {
                        (0.40f * rawWifi + 0.60f * smoothedWifiSpeedMBps)
                    }

                    smoothedCellSpeedMBps = if (smoothedCellSpeedMBps <= 0.001f) {
                        rawCell
                    } else {
                        (0.40f * rawCell + 0.60f * smoothedCellSpeedMBps)
                    }

                    if (rawWifi == 0f && smoothedWifiSpeedMBps < 0.015f) smoothedWifiSpeedMBps = 0f
                    if (rawCell == 0f && smoothedCellSpeedMBps < 0.015f) smoothedCellSpeedMBps = 0f
                } else {
                    smoothedWifiSpeedMBps = 0f
                    smoothedCellSpeedMBps = 0f
                }

                val wifiSpeedMBps = smoothedWifiSpeedMBps
                val cellSpeedMBps = smoothedCellSpeedMBps
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
