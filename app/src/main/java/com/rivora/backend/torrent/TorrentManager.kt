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
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production-Grade BitTorrent Session Manager for Rivora.
 *
 * Capabilities:
 * - High-speed Magnet URI resolution with DHT / PEX / LSD.
 * - Local and remote .torrent file decoding.
 * - Sequential media piece downloading mode.
 * - Real-time piece availability & completion bitmap telemetry.
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
    }

    private val sessionManager = SessionManager()
    private val isInitialized = AtomicBoolean(false)
    private var activeHandle: TorrentHandle? = null
    private var pollJob: Job? = null

    private val _activeTorrent = MutableStateFlow<TorrentInfoModel?>(null)
    val activeTorrent: StateFlow<TorrentInfoModel?> = _activeTorrent.asStateFlow()

    private var onFinishedCallback: ((TorrentInfoModel) -> Unit)? = null

    fun setOnFinishedCallback(callback: (TorrentInfoModel) -> Unit) {
        onFinishedCallback = callback
    }

    fun startSession() {
        if (isInitialized.getAndSet(true)) return

        scope.launch {
            try {
                sessionManager.addListener(object : AlertListener {
                    override fun types(): IntArray? = null

                    override fun alert(alert: Alert<*>) {
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
                                    Log.d(TAG, "Metadata received for: " + (alert.torrentName() ?: ""))
                                    activeHandle = h
                                    updateTorrentModel(h)
                                }
                            }
                            AlertType.ADD_TORRENT -> {
                                val addAlert = alert as? AddTorrentAlert
                                val h = addAlert?.handle()
                                if (h != null && h.isValid) {
                                    Log.d(TAG, "Torrent added to session: " + (alert.torrentName() ?: ""))
                                    activeHandle = h
                                    updateTorrentModel(h)
                                }
                            }
                            AlertType.TORRENT_FINISHED -> {
                                val finishAlert = alert as? TorrentFinishedAlert
                                val h = finishAlert?.handle()
                                if (h != null && h.isValid) {
                                    Log.d(TAG, "Torrent finished: " + (alert.torrentName() ?: ""))
                                    activeHandle = h
                                    updateTorrentModel(h)
                                    val current = _activeTorrent.value
                                    if (current != null) {
                                        onFinishedCallback?.invoke(current.copy(status = LocalTorrentStatus.FINISHED))
                                    }
                                }
                            }
                            AlertType.TORRENT_ERROR -> {
                                Log.w(TAG, "Torrent error alert: " + alert.message())
                            }
                            else -> {}
                        }
                    }
                })

                val sp = SettingsPack()
                sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
                sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), 200)
                sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 8)
                sp.setInteger(settings_pack.int_types.active_seeds.swigValue(), 4)
                sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 12)
                sp.setString(settings_pack.string_types.user_agent.swigValue(), "Rivora/1.1.3 (Android)")

                sessionManager.start()
                sessionManager.applySettings(sp)
                sessionManager.startDht()
                Log.d(TAG, "BitTorrent SessionManager successfully initialized with DHT/PEX.")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BitTorrent session: " + e.message, e)
            }
        }
    }

    fun downloadMagnet(magnetUri: String, saveDir: File, suggestedName: String? = null): TorrentInfoModel {
        startSession()

        val cleanName = suggestedName ?: parseMagnetName(magnetUri)
        val initialModel = TorrentInfoModel(
            infoHash = extractInfoHash(magnetUri),
            name = cleanName,
            status = LocalTorrentStatus.DOWNLOADING_METADATA,
            savePath = saveDir.absolutePath
        )
        _activeTorrent.value = initialModel

        scope.launch {
            try {
                saveDir.mkdirs()
                sessionManager.download(magnetUri, saveDir, torrent_flags_t())
                startTelemetryPoll()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading magnet: " + e.message, e)
            }
        }
        return initialModel
    }

    fun downloadTorrentFile(file: File, saveDir: File): TorrentInfoModel {
        startSession()
        val ti = TorrentInfo(file)
        return startTorrentInfoDownload(ti, saveDir)
    }

    fun downloadTorrentBytes(bytes: ByteArray, saveDir: File): TorrentInfoModel {
        startSession()
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

        scope.launch {
            try {
                saveDir.mkdirs()
                sessionManager.download(ti, saveDir)
                startTelemetryPoll()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading torrent info: " + e.message, e)
            }
        }
        return initialModel
    }

    fun pause() {
        activeHandle?.pause()
        _activeTorrent.value = _activeTorrent.value?.copy(status = LocalTorrentStatus.PAUSED)
    }

    fun resume() {
        activeHandle?.resume()
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
            } catch (e: Exception) {
                Log.w(TAG, "Error removing torrent: " + e.message)
            }
        }
        activeHandle = null
        _activeTorrent.value = null
    }

    fun setSequentialDownload(sequential: Boolean) {
        val handle = activeHandle ?: return
        if (handle.isValid) {
            if (sequential) {
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            } else {
                handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            }
        }
    }

    private fun startTelemetryPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(500)
                val handle = activeHandle
                if (handle != null && handle.isValid) {
                    updateTorrentModel(handle)
                }
            }
        }
    }

    private fun updateTorrentModel(handle: TorrentHandle) {
        try {
            val status = handle.status()
            val totalBytes = if (status.total() > 0) status.total() else status.totalWanted()
            val totalDone = status.totalDone()
            val downRate = status.downloadPayloadRate() / (1024f * 1024f)
            val upRate = status.uploadPayloadRate() / (1024f * 1024f)

            val piecesCount = status.numPieces()
            val piecesBitfield = status.pieces()
            var piecesCompleted = 0
            val bitmap = BooleanArray(piecesCount)
            if (piecesBitfield != null) {
                for (i in 0 until piecesCount) {
                    val bit = piecesBitfield.getBit(i)
                    bitmap[i] = bit
                    if (bit) piecesCompleted++
                }
            }

            val etaSec = if (downRate > 0.001f && totalBytes > totalDone) {
                ((totalBytes - totalDone) / (downRate * 1024f * 1024f)).toLong()
            } else 0L

            val isPaused = status.flags().and_(TorrentFlags.PAUSED).non_zero()
            val torrentStatus = when {
                status.isFinished || status.isSeeding -> LocalTorrentStatus.FINISHED
                isPaused -> LocalTorrentStatus.PAUSED
                status.state() == org.libtorrent4j.TorrentStatus.State.CHECKING_FILES ||
                status.state() == org.libtorrent4j.TorrentStatus.State.CHECKING_RESUME_DATA -> LocalTorrentStatus.CHECKING_FILES
                status.state() == org.libtorrent4j.TorrentStatus.State.DOWNLOADING_METADATA -> LocalTorrentStatus.DOWNLOADING_METADATA
                status.state() == org.libtorrent4j.TorrentStatus.State.DOWNLOADING -> LocalTorrentStatus.DOWNLOADING
                else -> LocalTorrentStatus.DOWNLOADING
            }

            val currentModel = _activeTorrent.value
            val name = status.name().ifBlank { currentModel?.name ?: ("torrent_" + handle.infoHash().toHex()) }

            val updated = TorrentInfoModel(
                infoHash = handle.infoHash().toHex(),
                name = name,
                totalBytes = totalBytes,
                downloadedBytes = totalDone,
                uploadedBytes = status.totalPayloadUpload(),
                downloadRateMBps = downRate,
                uploadRateMBps = upRate,
                numPeers = status.numPeers(),
                numSeeds = status.numSeeds(),
                totalPeers = status.listPeers(),
                totalSeeds = status.listSeeds(),
                progress = status.progress(),
                status = torrentStatus,
                etaSeconds = etaSec,
                piecesCount = piecesCount,
                piecesCompleted = piecesCompleted,
                piecesBitmap = bitmap,
                savePath = handle.savePath()
            )

            _activeTorrent.value = updated

        } catch (e: Exception) {
            Log.w(TAG, "Error reading torrent telemetry: " + e.message)
        }
    }

    private fun parseMagnetName(magnet: String): String {
        val dnRegex = """[?&]dn=([^&]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = dnRegex.find(magnet)
        if (match != null) {
            return try {
                java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
            } catch (_: Exception) {
                match.groupValues[1]
            }
        }
        val xtRegex = """xt=urn:btih:([a-zA-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        return xtRegex.find(magnet)?.groupValues?.get(1)?.let { "torrent_" + it } ?: "torrent_download"
    }

    private fun extractInfoHash(magnet: String): String {
        val xtRegex = """xt=urn:btih:([a-zA-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        return xtRegex.find(magnet)?.groupValues?.get(1) ?: ""
    }
}
