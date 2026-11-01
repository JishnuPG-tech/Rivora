package com.rivora.backend.torrent

import android.content.Context
import android.util.Log
import com.rivora.backend.torrent.TorrentStatus as LocalTorrentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production-Grade BitTorrent Session Manager for Rivora.
 *
 * Capabilities:
 * - High-speed Magnet URI resolution with DHT / PEX / LSD.
 * - Automatic injection of high-speed public fallback trackers to resolve bare magnets in seconds.
 * - Local and remote .torrent file decoding.
 * - Sequential media piece downloading mode.
 * - Safe boundary-checked bitfield & piece availability telemetry (no JNI crashes).
 * - Seamless integration with Rivora's DownloadEngine & ChunkMatrixCanvas.
 */
class TorrentManager private constructor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TorrentManager"

        @Volatile
        private var INSTANCE: TorrentManager? = null

        fun getInstance(context: Context? = null): TorrentManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context?.applicationContext 
                    ?: throw IllegalStateException("TorrentManager must be initialized with a Context")
                INSTANCE ?: TorrentManager(appCtx).also { INSTANCE = it }
            }
        }

        val FALLBACK_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "http://tracker.openbittorrent.com:80/announce"
        )
    }

    private val sessionManager = SessionManager()
    private val sessionMutex = Mutex()
    private val isSessionRunning = AtomicBoolean(false)
    private var activeHandle: TorrentHandle? = null
    private var pollJob: Job? = null

    private val _activeTorrent = MutableStateFlow<TorrentInfoModel?>(null)
    val activeTorrent: StateFlow<TorrentInfoModel?> = _activeTorrent.asStateFlow()

    private var onFinishedCallback: ((TorrentInfoModel) -> Unit)? = null

    fun setOnFinishedCallback(callback: (TorrentInfoModel) -> Unit) {
        onFinishedCallback = callback
    }

    suspend fun ensureSessionStarted() = withContext(Dispatchers.IO) {
        if (isSessionRunning.get() && sessionManager.isRunning) return@withContext
        sessionMutex.withLock {
            if (!sessionManager.isRunning) {
                try {
                    sessionManager.addListener(object : AlertListener {
                        override fun types(): IntArray? = null

                        override fun alert(alert: Alert<*>) {
                            try {
                                val torrentAlert = alert as? TorrentAlert<*>
                                val handle = torrentAlert?.handle()
                                if (handle != null && handle.isValid) {
                                    activeHandle = handle
                                }

                                when (alert.type()) {
                                    AlertType.METADATA_RECEIVED -> {
                                        val metaAlert = alert as? MetadataReceivedAlert
                                        val h = metaAlert?.handle()
                                        if (h != null && h.isValid) {
                                            val tName = try { torrentAlert?.torrentName() ?: "" } catch (_: Throwable) { "" }
                                            Log.d(TAG, "Metadata received for torrent: $tName")
                                            activeHandle = h
                                            updateTorrentModel(h)
                                        }
                                    }
                                    AlertType.ADD_TORRENT -> {
                                        val addAlert = alert as? AddTorrentAlert
                                        val h = addAlert?.handle()
                                        if (h != null && h.isValid) {
                                            val tName = try { torrentAlert?.torrentName() ?: "" } catch (_: Throwable) { "" }
                                            Log.d(TAG, "Torrent added to session: $tName")
                                            activeHandle = h
                                            try {
                                                FALLBACK_TRACKERS.forEach { tr ->
                                                    try { h.addTracker(AnnounceEntry(tr)) } catch (_: Throwable) {}
                                                }
                                                h.resume()
                                            } catch (_: Throwable) {}
                                            updateTorrentModel(h)
                                        }
                                    }
                                    AlertType.TORRENT_FINISHED -> {
                                        val finishAlert = alert as? TorrentFinishedAlert
                                        val h = finishAlert?.handle()
                                        if (h != null && h.isValid) {
                                            val tName = try { torrentAlert?.torrentName() ?: "" } catch (_: Throwable) { "" }
                                            Log.d(TAG, "Torrent finished: $tName")
                                            activeHandle = h
                                            updateTorrentModel(h)
                                            val current = _activeTorrent.value
                                            if (current != null) {
                                                onFinishedCallback?.invoke(current.copy(status = LocalTorrentStatus.FINISHED))
                                            }
                                        }
                                    }
                                    AlertType.TORRENT_ERROR -> {
                                        Log.w(TAG, "Torrent error alert: " + (try { alert.message() } catch (_: Throwable) { "error" }))
                                    }
                                    else -> {}
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "Unhandled exception in libtorrent alert: " + t.message, t)
                            }
                        }
                    })

                    val sp = SessionParams()
                    val settings = sp.settings
                    settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
                    settings.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
                    settings.setInteger(settings_pack.int_types.connections_limit.swigValue(), 200)
                    settings.setInteger(settings_pack.int_types.active_downloads.swigValue(), 10)
                    settings.setInteger(settings_pack.int_types.active_seeds.swigValue(), 4)
                    settings.setInteger(settings_pack.int_types.active_limit.swigValue(), 15)
                    settings.setString(settings_pack.string_types.user_agent.swigValue(), "Rivora/1.1.4 (Android)")
                    settings.setDhtBootstrapNodes("dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881")

                    sessionManager.start(sp)
                    sessionManager.startDht()
                    isSessionRunning.set(true)
                    Log.d(TAG, "BitTorrent SessionManager successfully initialized with DHT/PEX.")

                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start BitTorrent session: " + t.message, t)
                }
            }
        }
    }

    fun startSession() {
        scope.launch {
            ensureSessionStarted()
        }
    }

    private fun appendTrackersIfMissing(magnetUri: String): String {
        if (!magnetUri.startsWith("magnet:", ignoreCase = true)) return magnetUri
        val sb = StringBuilder(magnetUri)
        FALLBACK_TRACKERS.forEach { tracker ->
            val encoded = try { URLEncoder.encode(tracker, "UTF-8") } catch (_: Exception) { tracker }
            if (!magnetUri.contains(tracker, ignoreCase = true) && !magnetUri.contains(encoded, ignoreCase = true)) {
                sb.append("&tr=").append(encoded)
            }
        }
        return sb.toString()
    }

    fun downloadMagnet(magnetUri: String, saveDir: File, suggestedName: String? = null): TorrentInfoModel {
        val cleanName = suggestedName ?: parseMagnetName(magnetUri)
        val infoHash = extractInfoHash(magnetUri)
        val initialModel = TorrentInfoModel(
            infoHash = infoHash,
            name = cleanName,
            status = LocalTorrentStatus.DOWNLOADING_METADATA,
            savePath = saveDir.absolutePath
        )
        _activeTorrent.value = initialModel

        scope.launch(Dispatchers.IO) {
            try {
                ensureSessionStarted()
                saveDir.mkdirs()

                // Check if already registered in session
                if (infoHash.isNotBlank()) {
                    try {
                        val existing = sessionManager.find(Sha1Hash.parseHex(infoHash))
                        if (existing != null && existing.isValid) {
                            activeHandle = existing
                            FALLBACK_TRACKERS.forEach { tr ->
                                try { existing.addTracker(AnnounceEntry(tr)) } catch (_: Throwable) {}
                            }
                            existing.resume()
                            startTelemetryPoll()
                            return@launch
                        }
                    } catch (_: Throwable) {}
                }

                val enrichedMagnet = appendTrackersIfMissing(magnetUri)
                val flags = TorrentFlags.AUTO_MANAGED
                sessionManager.download(enrichedMagnet, saveDir, flags)
                Log.d(TAG, "Added magnet to libtorrent: $infoHash")

                delay(200)
                if (infoHash.isNotBlank()) {
                    try {
                        val handle = sessionManager.find(Sha1Hash.parseHex(infoHash))
                        if (handle != null && handle.isValid) {
                            activeHandle = handle
                            FALLBACK_TRACKERS.forEach { tr ->
                                try { handle.addTracker(AnnounceEntry(tr)) } catch (_: Throwable) {}
                            }
                            handle.resume()
                        }
                    } catch (_: Throwable) {}
                }
                startTelemetryPoll()
            } catch (t: Throwable) {
                Log.e(TAG, "Error downloading magnet: " + t.message, t)
            }
        }
        return initialModel
    }

    fun downloadTorrentFile(file: File, saveDir: File): TorrentInfoModel {
        val ti = TorrentInfo(file)
        return startTorrentInfoDownload(ti, saveDir)
    }

    fun downloadTorrentBytes(bytes: ByteArray, saveDir: File): TorrentInfoModel {
        val ti = TorrentInfo(bytes)
        return startTorrentInfoDownload(ti, saveDir)
    }

    private fun startTorrentInfoDownload(ti: TorrentInfo, saveDir: File): TorrentInfoModel {
        val totalBytes = ti.totalSize()
        val initialModel = TorrentInfoModel(
            infoHash = ti.infoHash().toHex(),
            name = ti.name(),
            totalBytes = totalBytes,
            piecesCount = ti.numPieces(),
            status = LocalTorrentStatus.DOWNLOADING,
            savePath = saveDir.absolutePath
        )
        _activeTorrent.value = initialModel

        scope.launch(Dispatchers.IO) {
            try {
                ensureSessionStarted()
                saveDir.mkdirs()
                sessionManager.download(ti, saveDir)
                delay(200)
                try {
                    val handle = sessionManager.find(ti.infoHash())
                    if (handle != null && handle.isValid) {
                        activeHandle = handle
                        FALLBACK_TRACKERS.forEach { tr ->
                            try { handle.addTracker(AnnounceEntry(tr)) } catch (_: Throwable) {}
                        }
                        handle.resume()
                    }
                } catch (_: Throwable) {}
                startTelemetryPoll()
            } catch (t: Throwable) {
                Log.e(TAG, "Error downloading torrent info: " + t.message, t)
            }
        }
        return initialModel
    }

    fun pause() {
        try {
            activeHandle?.pause()
        } catch (_: Throwable) {}
        _activeTorrent.value = _activeTorrent.value?.copy(status = LocalTorrentStatus.PAUSED)
    }

    fun resume() {
        try {
            activeHandle?.resume()
        } catch (_: Throwable) {}
        _activeTorrent.value = _activeTorrent.value?.copy(status = LocalTorrentStatus.DOWNLOADING)
    }

    fun cancel(deleteFiles: Boolean = true) {
        pollJob?.cancel()
        pollJob = null
        val handle = activeHandle
        if (handle != null && handle.isValid) {
            try {
                sessionManager.remove(handle)
                if (deleteFiles) {
                    val current = _activeTorrent.value
                    if (current != null) {
                        val file = File(current.savePath, current.name)
                        if (file.exists()) file.deleteRecursively()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error removing torrent: " + t.message)
            }
        }
        activeHandle = null
        _activeTorrent.value = null
    }

    fun setSequentialDownload(sequential: Boolean) {
        val handle = activeHandle ?: return
        try {
            if (handle.isValid) {
                if (sequential) {
                    handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                } else {
                    handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun startTelemetryPoll() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(500)
                try {
                    if (!sessionManager.isRunning) continue
                    var handle = activeHandle
                    if (handle == null || !handle.isValid) {
                        val currentHash = _activeTorrent.value?.infoHash
                        if (!currentHash.isNullOrBlank()) {
                            try {
                                val found = sessionManager.find(Sha1Hash.parseHex(currentHash))
                                if (found != null && found.isValid) {
                                    handle = found
                                    activeHandle = found
                                }
                            } catch (_: Throwable) {}
                        }
                        if (handle == null || !handle.isValid) {
                            try {
                                val list = sessionManager.swig().get_torrents()
                                if (list.isNotEmpty()) {
                                    handle = TorrentHandle(list[0])
                                    activeHandle = handle
                                }
                            } catch (_: Throwable) {}
                        }
                    }

                    if (handle != null && handle.isValid) {
                        updateTorrentModel(handle)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error in telemetry poll: " + t.message)
                }
            }
        }
    }

    private fun updateTorrentModel(handle: TorrentHandle) {
        try {
            if (!handle.isValid) return
            val status = try { handle.status() } catch (_: Throwable) { null } ?: return
            val totalBytes = try {
                if (status.total() > 0) status.total() else status.totalWanted()
            } catch (_: Throwable) { 0L }
            val totalDone = try { status.totalDone() } catch (_: Throwable) { 0L }
            val downRate = try { (status.downloadPayloadRate() / (1024f * 1024f)).coerceAtLeast(0f) } catch (_: Throwable) { 0f }
            val upRate = try { (status.uploadPayloadRate() / (1024f * 1024f)).coerceAtLeast(0f) } catch (_: Throwable) { 0f }

            val piecesCount = try { status.numPieces().coerceAtLeast(0) } catch (_: Throwable) { 0 }
            val piecesBitfield = try { status.pieces() } catch (_: Throwable) { null }
            var piecesCompleted = 0
            val bitmap = BooleanArray(piecesCount)
            if (piecesBitfield != null && piecesCount > 0) {
                val bitfieldSize = try { piecesBitfield.size() } catch (_: Throwable) { 0 }
                val limit = minOf(piecesCount, bitfieldSize)
                for (i in 0 until limit) {
                    val bit = try { piecesBitfield.getBit(i) } catch (_: Throwable) { false }
                    bitmap[i] = bit
                    if (bit) piecesCompleted++
                }
            }

            val etaSec = if (downRate > 0.001f && totalBytes > totalDone) {
                ((totalBytes - totalDone) / (downRate * 1024f * 1024f)).toLong().coerceAtLeast(0L)
            } else 0L

            val isPaused = try { status.flags().and_(TorrentFlags.PAUSED).non_zero() } catch (_: Throwable) { false }
            val stateVal = try { status.state() } catch (_: Throwable) { null }
            val isFinished = try { status.isFinished } catch (_: Throwable) { false }
            val isSeeding = try { status.isSeeding } catch (_: Throwable) { false }

            val torrentStatus = when {
                isFinished || isSeeding -> LocalTorrentStatus.FINISHED
                isPaused -> LocalTorrentStatus.PAUSED
                stateVal == org.libtorrent4j.TorrentStatus.State.CHECKING_FILES ||
                stateVal == org.libtorrent4j.TorrentStatus.State.CHECKING_RESUME_DATA -> LocalTorrentStatus.CHECKING_FILES
                stateVal == org.libtorrent4j.TorrentStatus.State.DOWNLOADING_METADATA -> LocalTorrentStatus.DOWNLOADING_METADATA
                stateVal == org.libtorrent4j.TorrentStatus.State.DOWNLOADING -> LocalTorrentStatus.DOWNLOADING
                else -> LocalTorrentStatus.DOWNLOADING
            }

            val currentModel = _activeTorrent.value
            val infoHashHex = try { handle.infoHash()?.toHex() ?: "" } catch (_: Throwable) { "" }
            val rawName = try { status.name() } catch (_: Throwable) { null }
            val name = if (!rawName.isNullOrBlank()) {
                rawName
            } else {
                currentModel?.name ?: (if (infoHashHex.isNotBlank()) "torrent_$infoHashHex" else "torrent_download")
            }

            val savePath = try { handle.savePath() ?: "" } catch (_: Throwable) { "" }

            val updated = TorrentInfoModel(
                infoHash = infoHashHex,
                name = name,
                totalBytes = totalBytes,
                downloadedBytes = totalDone,
                uploadedBytes = try { status.totalPayloadUpload() } catch (_: Throwable) { 0L },
                downloadRateMBps = downRate,
                uploadRateMBps = upRate,
                numPeers = try { status.numPeers().coerceAtLeast(0) } catch (_: Throwable) { 0 },
                numSeeds = try { status.numSeeds().coerceAtLeast(0) } catch (_: Throwable) { 0 },
                totalPeers = try { status.listPeers().coerceAtLeast(0) } catch (_: Throwable) { 0 },
                totalSeeds = try { status.listSeeds().coerceAtLeast(0) } catch (_: Throwable) { 0 },
                progress = try { status.progress().coerceIn(0f, 1f) } catch (_: Throwable) { 0f },
                status = torrentStatus,
                etaSeconds = etaSec,
                piecesCount = piecesCount,
                piecesCompleted = piecesCompleted,
                piecesBitmap = bitmap,
                savePath = savePath
            )

            _activeTorrent.value = updated

        } catch (t: Throwable) {
            Log.w(TAG, "Error reading torrent telemetry: " + t.message, t)
        }
    }

    private fun parseMagnetName(magnet: String): String {
        val dnRegex = """[?&]dn=([^&]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = dnRegex.find(magnet)
        if (match != null) {
            return try {
                URLDecoder.decode(match.groupValues[1], "UTF-8")
            } catch (_: Exception) {
                match.groupValues[1]
            }
        }
        val xtRegex = """xt=urn:btih:([a-zA-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        return xtRegex.find(magnet)?.groupValues?.get(1)?.let { "torrent_" + it } ?: "torrent_download"
    }

    private fun extractInfoHash(magnet: String): String {
        val xtRegex = """xt=urn:btih:([a-zA-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = xtRegex.find(magnet)
        if (match != null) return match.groupValues[1]

        val rawHash = magnet.trim()
        if (rawHash.matches(Regex("^[a-fA-F0-9]{40}$"))) {
            return rawHash
        }
        return ""
    }
}
