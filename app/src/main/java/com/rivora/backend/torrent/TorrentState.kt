package com.rivora.backend.torrent

enum class TorrentStatus {
    CHECKING_FILES,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    FINISHED,
    SEEDING,
    ALLOCATING,
    PAUSED,
    ERROR
}

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val priority: Int = 4 // 0: don't download, 4: normal, 7: top
)

data class TorrentInfoModel(
    val infoHash: String,
    val name: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val downloadRateMBps: Float = 0f,
    val uploadRateMBps: Float = 0f,
    val numPeers: Int = 0,
    val numSeeds: Int = 0,
    val totalPeers: Int = 0,
    val totalSeeds: Int = 0,
    val progress: Float = 0f,
    val status: TorrentStatus = TorrentStatus.DOWNLOADING_METADATA,
    val etaSeconds: Long = 0L,
    val piecesCount: Int = 0,
    val piecesCompleted: Int = 0,
    val piecesBitmap: BooleanArray = BooleanArray(0),
    val files: List<TorrentFileEntry> = emptyList(),
    val savePath: String = ""
)
