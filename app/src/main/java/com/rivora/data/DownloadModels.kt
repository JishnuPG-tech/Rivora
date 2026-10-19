package com.rivora.data

enum class DownloadStatus {
  ACTIVE,
  PAUSED,
  FINISHED,
  QUEUED,
  CANCELED
}

data class NetworkAdapter(
  val id: String,
  val name: String,
  val interfaceName: String,
  val isConnected: Boolean,
  val streams: Int,
  val currentSpeedMBps: Float,
  val downloadedBytes: Long,
  val isPrimary: Boolean
)

data class DownloadItem(
  val id: String,
  val fileName: String,
  val fileExtension: String,
  val totalBytes: Long,
  val downloadedBytes: Long,
  val status: DownloadStatus,
  val host: String = "",
  val timeAgo: String = "",
  val destinationPath: String = "Downloads/",
  val destinationUri: String? = null,
  val downloadUrl: String = ""
)
