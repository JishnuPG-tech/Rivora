package com.rivora.ui.screens

import androidx.compose.runtime.Composable
import com.rivora.updater.GitHubRelease
import com.rivora.updater.UpdateDownloadState
import java.io.File

/**
 * Backward compatibility wrapper delegating to the full-page UpdateScreen.
 */
@Composable
fun UpdateDialog(
    currentVersion: String,
    newVersion: String,
    release: GitHubRelease,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onDismiss: () -> Unit
) {
    UpdateScreen(
        currentVersion = currentVersion,
        newVersion = newVersion,
        release = release,
        downloadState = downloadState,
        onStartDownload = onStartDownload,
        onInstallApk = onInstallApk,
        onDismiss = onDismiss
    )
}
