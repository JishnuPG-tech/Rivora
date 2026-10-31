package com.rivora.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraInsetBox
import com.rivora.ui.components.RivoraSwitch
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.updater.UpdateCheckResult

/**
 * Production Settings Modal for Rivora.
 * Includes direct GitHub Releases update checking, BitTorrent protocol options,
 * and quick access to network limits.
 */
@Composable
fun SettingsDialog(
    currentVersion: String = "1.1.4",
    isCheckingUpdate: Boolean = false,
    updateResult: UpdateCheckResult? = null,
    onCheckForUpdates: () -> Unit,
    onViewUpdate: () -> Unit = {},
    onOpenLimits: () -> Unit,
    onDismiss: () -> Unit
) {
    var dhtEnabled by remember { mutableStateOf(true) }
    var lsdEnabled by remember { mutableStateOf(true) }
    var trackersEnabled by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Settings",
                            style = RivoraType.rivoraText(RivoraType.Sans, 20f, FontWeight.SemiBold, 0f, 24f).copy(
                                color = RivoraColor.TextPrimary
                            )
                        )
                        Text(
                            text = "Preferences & Updates",
                            style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Normal, 0f, 16f).copy(
                                color = RivoraColor.TextMuted
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RivoraColor.BorderSubtle)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v$currentVersion",
                            style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Medium, 0f, 14f).copy(
                                color = RivoraColor.OrangeBright
                            )
                        )
                    }
                }

                // 1. App Updates Section
                RivoraCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = "Updates",
                                tint = RivoraColor.Orange,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "App Updates",
                                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = RivoraColor.Orange,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // Update Status Banner
                    RivoraInsetBox(padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                isCheckingUpdate -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Checking GitHub Releases...",
                                            style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.Normal, 0f, 16f).copy(
                                                color = RivoraColor.TextSecondary
                                            )
                                        )
                                    }
                                }
                                updateResult is UpdateCheckResult.UpdateAvailable -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(RivoraColor.OrangeBright)
                                            )
                                            Text(
                                                text = "New version ${updateResult.newVersion} available!",
                                                style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.SemiBold, 0f, 16f).copy(
                                                    color = RivoraColor.OrangeBright
                                                )
                                            )
                                        }
                                        RivoraButton(
                                            text = "Install Update",
                                            onClick = {
                                                onDismiss()
                                                onViewUpdate()
                                            },
                                            variant = RivoraButtonVariant.AMBER,
                                            height = 36.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                updateResult is UpdateCheckResult.UpToDate -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Up to date",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "You're on the latest version",
                                            style = RivoraType.rivoraText(RivoraType.Sans, 13f, FontWeight.Normal, 0f, 16f).copy(
                                                color = Color(0xFF10B981)
                                            )
                                        )
                                    }
                                }
                                updateResult is UpdateCheckResult.Error -> {
                                    Text(
                                        text = updateResult.message,
                                        style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Normal, 0f, 15f).copy(
                                            color = RivoraColor.StreamSecondary
                                        )
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Rivora auto-checks for updates via GitHub Releases.",
                                        style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.Normal, 0f, 16f).copy(
                                            color = RivoraColor.TextMuted
                                        )
                                    )
                                }
                            }

                            if (!isCheckingUpdate && updateResult !is UpdateCheckResult.UpdateAvailable) {
                                RivoraButton(
                                    text = "Check for Updates Now",
                                    onClick = onCheckForUpdates,
                                    variant = RivoraButtonVariant.DARK,
                                    height = 36.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 2. BitTorrent Protocol Settings
                RivoraCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "BitTorrent",
                            tint = RivoraColor.Orange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "BitTorrent Protocol",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    RivoraInsetBox(padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "DHT Peer Discovery",
                                        style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                                    )
                                    Text(
                                        text = "Find peers without centralized trackers",
                                        style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.Normal, 0f, 14f).copy(
                                            color = RivoraColor.TextMuted
                                        )
                                    )
                                }
                                RivoraSwitch(
                                    checked = dhtEnabled,
                                    onCheckedChange = { dhtEnabled = it },
                                    activeTrackColor = RivoraColor.Orange
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "High-Speed Fallback Trackers",
                                        style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                                    )
                                    Text(
                                        text = "Auto-inject public trackers for bare magnets",
                                        style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.Normal, 0f, 14f).copy(
                                            color = RivoraColor.TextMuted
                                        )
                                    )
                                }
                                RivoraSwitch(
                                    checked = trackersEnabled,
                                    onCheckedChange = { trackersEnabled = it },
                                    activeTrackColor = RivoraColor.Orange
                                )
                            }
                        }
                    }
                }

                // 3. Network & Storage Shortcuts
                RivoraCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onDismiss()
                                    onOpenLimits()
                                }
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = "Network Limits",
                                tint = RivoraColor.Orange,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Network Limits & Budget",
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Configure cellular speed caps & data caps",
                                    style = RivoraType.rivoraText(RivoraType.Sans, 11.5f, FontWeight.Normal, 0f, 14f).copy(
                                        color = RivoraColor.TextMuted
                                    )
                                )
                            }
                        }
                        Text(
                            text = "Edit ▾",
                            style = RivoraType.ButtonLabel.copy(color = RivoraColor.OrangeBright, fontSize = 12.sp)
                        )
                    }
                }

                // Close Button
                RivoraButton(
                    text = "Done",
                    onClick = onDismiss,
                    variant = RivoraButtonVariant.DARK,
                    height = 42.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
