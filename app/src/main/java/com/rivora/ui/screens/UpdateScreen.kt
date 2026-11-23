package com.rivora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.R
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.updater.GitHubRelease
import com.rivora.updater.UpdateDownloadState
import com.rivora.util.ByteFormatter
import java.io.File

data class CleanChangelogSection(
    val title: String,
    val items: List<String>
)

object ChangelogCleaner {
    /**
     * Parses raw GitHub markdown, completely strips markdown formatting (#, **, *, `, etc.),
     * and structures updates into clean, organized sections with bullet points.
     */
    fun parse(rawMarkdown: String): List<CleanChangelogSection> {
        if (rawMarkdown.isBlank()) {
            return listOf(
                CleanChangelogSection(
                    title = "General Improvements",
                    items = listOf("Performance optimizations, bug fixes, and stability improvements.")
                )
            )
        }

        val lines = rawMarkdown.lines()
        val sections = mutableListOf<CleanChangelogSection>()
        var currentTitle = "Key Highlights"
        val currentItems = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Filter out top-level repetitive headers (e.g. "### What's New in Rivora...")
            if (trimmed.startsWith("#") && (trimmed.contains("What's New", ignoreCase = true) || trimmed.contains("Release", ignoreCase = true))) {
                continue
            }

            // Section heading (### or #### or ##)
            if (trimmed.startsWith("#")) {
                if (currentItems.isNotEmpty()) {
                    sections.add(CleanChangelogSection(currentTitle, currentItems.toList()))
                    currentItems.clear()
                }
                currentTitle = sanitizeText(trimmed.trimStart('#').trim())
                continue
            }

            // Bullet points (- or * or •)
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) {
                val bulletContent = trimmed.substring(1).trim()
                val cleanedBullet = sanitizeText(bulletContent)
                if (cleanedBullet.isNotBlank()) {
                    currentItems.add(cleanedBullet)
                }
            } else {
                val cleaned = sanitizeText(trimmed)
                if (cleaned.isNotBlank()) {
                    currentItems.add(cleaned)
                }
            }
        }

        if (currentItems.isNotEmpty()) {
            sections.add(CleanChangelogSection(currentTitle, currentItems.toList()))
        }

        return sections.ifEmpty {
            listOf(
                CleanChangelogSection(
                    title = "Release Notes",
                    items = listOf(sanitizeText(rawMarkdown))
                )
            )
        }
    }

    private fun sanitizeText(text: String): String {
        return text
            // Strip markdown bold and italics
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""\*([^*]+)\*"""), "$1")
            .replace(Regex("""__([^_]+)__"""), "$1")
            .replace(Regex("""_([^_]+)_"""), "$1")
            // Strip inline code blocks
            .replace(Regex("""`([^`]+)`"""), "$1")
            // Strip markdown links [label](url) -> label
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
            // Remove any remaining raw markdown symbols
            .replace("#", "")
            .replace("*", "")
            .trim()
    }
}

@Composable
fun UpdateScreen(
    currentVersion: String,
    newVersion: String,
    release: GitHubRelease,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {
        onDismiss()
    }

    val parsedSections = remember(release.body) {
        ChangelogCleaner.parse(release.body)
    }

    val apkSize = release.apkAsset?.sizeBytes ?: 0L
    val formattedSize = if (apkSize > 0L) ByteFormatter.formatBytes(apkSize) else "28 MB"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RivoraColor.App)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF191920))
                            .border(1.dp, RivoraColor.BorderSubtle, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = RivoraColor.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SOFTWARE UPDATE",
                            style = RivoraType.rivoraText(RivoraType.Mono, 10.5f, FontWeight.Bold, 0.8f, 14f).copy(
                                color = RivoraColor.TextMuted
                            )
                        )
                        Text(
                            text = "Rivora " + newVersion,
                            style = RivoraType.rivoraText(RivoraType.Sans, 16f, FontWeight.Bold, 0f, 20f).copy(
                                color = RivoraColor.TextPrimary
                            )
                        )
                    }
                }

                // Version Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(RivoraColor.Orange.copy(alpha = 0.12f))
                        .border(1.dp, RivoraColor.Orange.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = newVersion,
                        style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Bold, 0f, 15f).copy(
                            color = RivoraColor.Orange
                        )
                    )
                }
            }

            // 2. Scrollable Changelog & Details Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Overview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RivoraColor.Surface)
                        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF221710))
                                .border(1.5.dp, RivoraColor.Orange.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.rivora_logo),
                                contentDescription = "Rivora Logo",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = release.name.ifBlank { "Rivora Update $newVersion" },
                                style = RivoraType.rivoraText(RivoraType.Sans, 16f, FontWeight.Bold, 0f, 21f).copy(
                                    color = RivoraColor.TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Current: v" + currentVersion.removePrefix("v"),
                                    style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                                        color = RivoraColor.TextMuted
                                    )
                                )
                                Text(
                                    text = "•",
                                    style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                                        color = RivoraColor.TextMuted
                                    )
                                )
                                Text(
                                    text = formattedSize,
                                    style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Medium, 0f, 14f).copy(
                                        color = RivoraColor.Orange
                                    )
                                )
                            }
                        }
                    }
                }

                // Section Label
                Text(
                    text = "WHAT'S NEW IN THIS VERSION",
                    style = RivoraType.rivoraText(RivoraType.Mono, 10.5f, FontWeight.Bold, 0.8f, 14f).copy(
                        color = RivoraColor.TextMuted
                    ),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                // Clean Structured Changelog Sections
                parsedSections.forEach { section ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(RivoraColor.Surface)
                            .border(1.dp, Color(0xFF22222A), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Section Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(RivoraColor.Orange)
                                )
                                Text(
                                    text = section.title,
                                    style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.Bold, 0f, 18f).copy(
                                        color = RivoraColor.TextPrimary
                                    )
                                )
                            }

                            // Bullet Items
                            section.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        style = RivoraType.rivoraText(RivoraType.Mono, 13f, FontWeight.Bold, 0f, 19f).copy(
                                            color = RivoraColor.Orange.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(top = 1.dp)
                                    )

                                    val colonIndex = item.indexOf(':')
                                    if (colonIndex != -1 && colonIndex < 35) {
                                        val prefix = item.substring(0, colonIndex + 1)
                                        val suffix = item.substring(colonIndex + 1).trim()
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFFEDEDF2))) {
                                                    append(prefix + " ")
                                                }
                                                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color(0xFFB4B4BE))) {
                                                    append(suffix)
                                                }
                                            },
                                            style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.Normal, 0f, 19f)
                                        )
                                    } else {
                                        Text(
                                            text = item,
                                            style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.Normal, 0f, 19f).copy(
                                                color = Color(0xFFB4B4BE)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 3. Bottom Sticky Action Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFF141419))
                    .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(RivoraColor.Orange)
                                        )
                                        Text(
                                            text = "Downloading update...",
                                            style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.SemiBold, 0f, 16f).copy(
                                                color = RivoraColor.TextPrimary
                                            )
                                        )
                                    }

                                    Text(
                                        text = "${downloadState.progressPercent}%",
                                        style = RivoraType.rivoraText(RivoraType.Mono, 13f, FontWeight.Bold, 0f, 16f).copy(
                                            color = RivoraColor.Orange
                                        )
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { downloadState.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = RivoraColor.Orange,
                                    trackColor = Color(0xFF23232C)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = ByteFormatter.formatProgress(downloadState.bytesDownloaded, downloadState.totalBytes),
                                        style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                                            color = RivoraColor.TextMuted
                                        )
                                    )
                                }
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF132817))
                                    .border(1.dp, Color(0xFF2D6935), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Update downloaded. Ready to install.",
                                    style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.Medium, 0f, 16f).copy(
                                        color = Color(0xFF4ADE80)
                                    )
                                )
                            }
                        }

                        is UpdateDownloadState.Failed -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF281315))
                                    .border(1.dp, Color(0xFF6E282C), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = downloadState.error,
                                    style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 16f).copy(
                                        color = Color(0xFFFF6B6B)
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        UpdateDownloadState.Idle -> {}
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isDownloading = downloadState is UpdateDownloadState.Downloading
                        val isReady = downloadState is UpdateDownloadState.ReadyToInstall
                        val isFailed = downloadState is UpdateDownloadState.Failed

                        // Secondary Later button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E26))
                                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Later",
                                style = RivoraType.rivoraText(RivoraType.Sans, 13.5f, FontWeight.SemiBold, 0f, 18f).copy(
                                    color = RivoraColor.TextSecondary
                                )
                            )
                        }

                        // Primary Action button
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDownloading) Color(0xFF4A2A18) else RivoraColor.Orange)
                                .clickable(enabled = !isDownloading) {
                                    when {
                                        isReady -> onInstallApk((downloadState as UpdateDownloadState.ReadyToInstall).apkFile)
                                        else -> onStartDownload()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val buttonIcon = when {
                                    isReady -> Icons.Rounded.SystemUpdate
                                    else -> Icons.Rounded.Download
                                }
                                if (!isDownloading) {
                                    Icon(
                                        imageVector = buttonIcon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        isDownloading -> "Downloading..."
                                        isReady -> "Install Now"
                                        isFailed -> "Retry Update"
                                        else -> "Update Now"
                                    },
                                    style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.Bold, 0f, 18f).copy(
                                        color = if (isDownloading) Color(0xAAFFFFFF) else Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
