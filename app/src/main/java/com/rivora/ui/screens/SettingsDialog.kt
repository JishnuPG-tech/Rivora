package com.rivora.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
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
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraInsetBox
import com.rivora.ui.components.RivoraSwitch
import com.rivora.updater.UpdateCheckResult

/**
 * Clean, structured, professional Settings modal with short texts and perfect alignment.
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
    var trackersEnabled by remember { mutableStateOf(true) }
    var sequentialEnabled by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(RivoraColor.Surface)
                .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Title & Version Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = RivoraType.rivoraText(RivoraType.Sans, 18f, FontWeight.SemiBold, 0f, 22f).copy(
                            color = RivoraColor.TextPrimary
                        )
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RivoraColor.SurfaceRaised)
                            .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "v$currentVersion",
                            style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.SemiBold, 0f, 13f).copy(
                                color = RivoraColor.OrangeBright
                            )
                        )
                    }
                }

                // 1. App Updates
                RivoraCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = RivoraColor.Orange,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "App Updates",
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = when {
                                        isCheckingUpdate -> "Checking..."
                                        updateResult is UpdateCheckResult.UpdateAvailable -> "v${updateResult.newVersion} available"
                                        updateResult is UpdateCheckResult.UpToDate -> "Up to date"
                                        updateResult is UpdateCheckResult.Error -> "Check failed"
                                        else -> "Auto-check enabled"
                                    },
                                    style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.Normal, 0f, 14f).copy(
                                        color = when {
                                            updateResult is UpdateCheckResult.UpdateAvailable -> RivoraColor.OrangeBright
                                            updateResult is UpdateCheckResult.UpToDate -> Color(0xFF10B981)
                                            else -> RivoraColor.TextMuted
                                        }
                                    )
                                )
                            }
                        }

                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = RivoraColor.Orange,
                                strokeWidth = 2.dp
                            )
                        } else if (updateResult is UpdateCheckResult.UpdateAvailable) {
                            RivoraButton(
                                text = "Update",
                                onClick = {
                                    onDismiss()
                                    onViewUpdate()
                                },
                                variant = RivoraButtonVariant.AMBER,
                                height = 30.dp
                            )
                        } else {
                            RivoraButton(
                                text = "Check",
                                onClick = onCheckForUpdates,
                                variant = RivoraButtonVariant.DARK,
                                height = 30.dp
                            )
                        }
                    }
                }

                // 2. BitTorrent Protocol
                RivoraCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = RivoraColor.Orange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "BitTorrent",
                            style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    RivoraInsetBox(padding = 10.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Row 1: DHT
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DHT discovery",
                                    style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.Medium, 0f, 16f).copy(
                                        color = RivoraColor.TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                RivoraSwitch(
                                    checked = dhtEnabled,
                                    onCheckedChange = { dhtEnabled = it },
                                    activeTrackColor = RivoraColor.Orange
                                )
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RivoraColor.BorderSubtle))

                            // Row 2: Public Trackers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Public trackers",
                                    style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.Medium, 0f, 16f).copy(
                                        color = RivoraColor.TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                RivoraSwitch(
                                    checked = trackersEnabled,
                                    onCheckedChange = { trackersEnabled = it },
                                    activeTrackColor = RivoraColor.Orange
                                )
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RivoraColor.BorderSubtle))

                            // Row 3: Sequential mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sequential mode",
                                    style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.Medium, 0f, 16f).copy(
                                        color = RivoraColor.TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                RivoraSwitch(
                                    checked = sequentialEnabled,
                                    onCheckedChange = { sequentialEnabled = it },
                                    activeTrackColor = RivoraColor.Orange
                                )
                            }
                        }
                    }
                }

                // 3. Network Limits
                RivoraCard(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onDismiss()
                            onOpenLimits()
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = RivoraColor.Orange,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Network Limits",
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Speed & data budgets",
                                    style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.Normal, 0f, 14f).copy(
                                        color = RivoraColor.TextMuted
                                    )
                                )
                            }
                        }
                        Text(
                            text = "Edit ▾",
                            style = RivoraType.ButtonLabel.copy(
                                color = RivoraColor.OrangeBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                // 4. Storage
                RivoraCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = RivoraColor.Orange,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Download Path",
                                    style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Downloads/Rivora",
                                    style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                                        color = RivoraColor.TextMuted
                                    )
                                )
                            }
                        }
                        Text(
                            text = "Default",
                            style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.Medium, 0f, 14f).copy(
                                color = RivoraColor.TextDisabled
                            ),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                // Done Button
                RivoraButton(
                    text = "Done",
                    onClick = onDismiss,
                    variant = RivoraButtonVariant.DARK,
                    height = 38.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
