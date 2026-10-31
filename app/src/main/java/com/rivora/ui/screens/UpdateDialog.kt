package com.rivora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.updater.GitHubRelease
import com.rivora.updater.UpdateDownloadState
import com.rivora.util.ByteFormatter

@Composable
fun UpdateDialog(
    currentVersion: String,
    newVersion: String,
    release: GitHubRelease,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "New Version Available",
                            style = RivoraType.rivoraText(RivoraType.Sans, 16f, FontWeight.Bold, 0f, 22f).copy(
                                color = RivoraColor.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Current: v" + currentVersion.removePrefix("v"),
                            style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 15f).copy(
                                color = RivoraColor.TextMuted
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RivoraColor.Orange.copy(alpha = 0.15f))
                            .border(1.dp, RivoraColor.Orange.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = newVersion,
                            style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Bold, 0f, 16f).copy(
                                color = RivoraColor.Orange
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RivoraColor.App)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = release.name.ifBlank { newVersion },
                        style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.SemiBold, 0f, 16f).copy(
                            color = RivoraColor.TextPrimary
                        ),
                        maxLines = 1
                    )

                    val apkSize = release.apkAsset?.sizeBytes ?: 0L
                    if (apkSize > 0L) {
                        Text(
                            text = ByteFormatter.formatBytes(apkSize),
                            style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Medium, 0f, 14f).copy(
                                color = RivoraColor.TextSecondary
                            )
                        )
                    }
                }

                Text(
                    text = "CHANGELOG",
                    style = RivoraType.rivoraText(RivoraType.Mono, 10f, FontWeight.Bold, 0.8f, 13f).copy(
                        color = RivoraColor.TextMuted
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RivoraColor.App)
                        .border(1.dp, Color(0xFF26262E), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = release.body.ifBlank { "No changelog notes provided with this release." },
                        style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Normal, 0f, 17f).copy(
                            color = Color(0xFFC0C0C8)
                        )
                    )
                }

                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading update... " + downloadState.progressPercent + "%",
                                    style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Medium, 0f, 15f).copy(
                                        color = RivoraColor.Orange
                                     )
                                )
                                Text(
                                    text = ByteFormatter.formatProgress(downloadState.bytesDownloaded, downloadState.totalBytes),
                                    style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                                        color = RivoraColor.TextSecondary
                                    )
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadState.progressPercent / 100f},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = RivoraColor.Orange,
                                trackColor = Color(0xFF222228)
                            )
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF142416))
                                .border(1.dp, Color(0xFF2E6335), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4ADE80))
                            )
                            Text(
                                text = "Downloaded! Tap 'Install Now' to upgrade.",
                                style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 16f).copy(
                                    color = Color(0xFF4ADE80)
                                )
                            )
                        }
                    }

                    is UpdateDownloadState.Failed -> {
                        Text(
                            text = "Error: " + downloadState.error,
                            style = RivoraType.rivoraText(RivoraType.Sans, 11.5f, FontWeight.Normal, 0f, 15f).copy(
                                color = Color(0xFFFF6B6B)
                            )
                        )
                    }

                    UpdateDownloadState.Idle -> {}
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1C22))
                            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Later",
                            style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.SemiBold, 0f, 17f).copy(
                                color = RivoraColor.TextSecondary
                            )
                        )
                    }

                    val isDownloading = downloadState is UpdateDownloadState.Downloading
                    val isReady = downloadState is UpdateDownloadState.ReadyToInstall

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDownloading) Color(0xFF442211) else RivoraColor.Orange)
                            .clickable(enabled = !isDownloading) {
                                if (isReady) {
                                    onInstallApk((downloadState as UpdateDownloadState.ReadyToInstall).apkFile)
                                } else {
                                    onStartDownload()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                isDownloading -> "Downloading..."
                                isReady -> "Install Now"
                                else -> "Update Now"
                            },
                            style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.Bold, 0f, 17f).copy(
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}
