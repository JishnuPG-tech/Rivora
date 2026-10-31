package com.rivora.updater

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkAsset: ReleaseAsset?
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: String,
        val release: GitHubRelease
    ) : UpdateCheckResult()

    data class UpToDate(
        val currentVersion: String
    ) : UpdateCheckResult()

    data class Error(
        val message: String
    ) : UpdateCheckResult()
}

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: java.io.File, val release: GitHubRelease) : UpdateDownloadState()
    data class Failed(val error: String) : UpdateDownloadState()
}
