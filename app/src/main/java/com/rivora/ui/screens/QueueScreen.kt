package com.rivora.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.R
import com.rivora.data.DownloadItem
import com.rivora.data.DownloadStatus
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.NetworkDot
import com.rivora.util.ByteFormatter
import com.rivora.ui.components.RivoraCard

/**
 * Downloads Screen (Rivora):
 * Clean, minimal, high-speed download manager.
 * Displays logo + Rivora, network indicator, download items when active,
 * "Click + to Download" when empty, and the bottom-right circular + floating button.
 */
@Composable
fun QueueScreen(
  activeItem: DownloadItem?,
  queue: List<DownloadItem>,
  finished: List<DownloadItem>,
  currentSpeedText: String,
  unitText: String,
  cellularActive: Boolean,
  isPaused: Boolean = false,
  onOpenDetail: (DownloadItem) -> Unit,
  onNavigateToAdd: () -> Unit,
  onOpenNetworks: () -> Unit,
  modifier: Modifier = Modifier,
  onTogglePause: () -> Unit = {},
  onCancelActive: () -> Unit = {},
  onStartQueued: (String) -> Unit = {},
  onRemoveQueued: (String) -> Unit = {},
  onOpenFile: (DownloadItem) -> Unit = {},
  onDeleteFinished: (DownloadItem) -> Unit = {},
  isWifiConnected: Boolean = false,
  isCellularConnected: Boolean = true
) {
  val activeCount = if (activeItem != null && (activeItem.status == DownloadStatus.ACTIVE || activeItem.status == DownloadStatus.PAUSED)) 1 else 0
  val queueCount = queue.size
  val activeNetworks = (if (isWifiConnected) 1 else 0) + (if (cellularActive && isCellularConnected) 1 else 0)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(RivoraColor.App)
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      // 1. Top Bar: Rivora Logo & Brand with Network Status Pill as before
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Image(
            painter = painterResource(id = R.drawable.rivora_logo),
            contentDescription = "Rivora Logo",
            modifier = Modifier
              .size(24.dp)
              .clip(RoundedCornerShape(6.dp))
          )
          Text(
            text = "Rivora",
            style = RivoraType.headingScreen.copy(
              color = RivoraColor.TextPrimary,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold
            )
          )
        }

        // Network Status Pill
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RivoraColor.Surface)
            .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(8.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onOpenNetworks
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          NetworkDot(
            color = if (isWifiConnected) RivoraColor.Orange else if (isCellularConnected) RivoraColor.StreamSecondary else RivoraColor.TextDisabled,
            size = 8.dp
          )
          Text(
            text = when (activeNetworks) {
              2 -> "2 networks ▾"
              1 -> "1 network ▾"
              else -> "Offline ▾"
            },
            style = RivoraType.ButtonLabel.copy(color = RivoraColor.TextPrimary, fontSize = 11.sp)
          )
        }
      }

      // 2. Queue Content List
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Section Header: Downloads
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Downloads",
              style = RivoraType.headingScreen.copy(color = RivoraColor.TextPrimary, fontSize = 22.sp)
            )
            if (activeCount > 0 || queueCount > 0) {
              Text(
                text = when {
                  activeCount > 0 && isPaused -> "1 PAUSED"
                  activeCount > 0 -> "$activeCount DOWNLOADING"
                  queueCount > 0 -> "$queueCount QUEUED"
                  else -> ""
                },
                style = RivoraType.Eyebrow.copy(
                  color = when {
                    activeCount > 0 && isPaused -> RivoraColor.Amber
                    activeCount > 0 -> RivoraColor.OrangeBright
                    else -> RivoraColor.TextSecondary
                  }
                )
              )
            }
          }
        }

        // CARD 1: Real Active Multi-Stream Download (if present)
        if (activeItem != null && (activeItem.status == DownloadStatus.ACTIVE || activeItem.status == DownloadStatus.PAUSED)) {
          val progress = if (activeItem.totalBytes > 0) {
            (activeItem.downloadedBytes.toFloat() / activeItem.totalBytes).coerceIn(0f, 1f)
          } else 0f

          val activeMB = activeItem.downloadedBytes / (1024f * 1024f)
          val totalMB = activeItem.totalBytes / (1024f * 1024f)

          item {
            RivoraCard(
              hasAura = true,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDetail(activeItem) }
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(RivoraColor.SurfaceRaised)
                      .border(1.dp, RivoraColor.Orange.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = activeItem.fileExtension.ifBlank { "FILE" },
                      style = RivoraType.rivoraText(RivoraType.Mono, 10f, FontWeight.Bold, 0f, 12f)
                        .copy(color = RivoraColor.OrangeBright)
                    )
                  }
                  Text(
                    text = activeItem.fileName,
                    style = RivoraType.RowName.copy(
                      color = RivoraColor.TextPrimary,
                      fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = "${(progress * 100).toInt()}%",
                    style = RivoraType.rivoraText(RivoraType.Mono, 16f, FontWeight.Bold, 0f, 20f)
                      .copy(color = if (isPaused) RivoraColor.Amber else RivoraColor.OrangeBright)
                  )

                  // Pause / Resume Quick Button
                  Box(
                    modifier = Modifier
                      .clip(CircleShape)
                      .background(RivoraColor.SurfaceRaised)
                      .border(1.dp, RivoraColor.BorderDefault, CircleShape)
                      .clickable { onTogglePause() }
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = if (isPaused) "▶" else "❚❚",
                      style = TextStyle(fontSize = 10.sp, color = RivoraColor.OrangeBright, fontWeight = FontWeight.Bold)
                    )
                  }

                  // Cancel Quick Button
                  Box(
                    modifier = Modifier
                      .clip(CircleShape)
                      .background(RivoraColor.SurfaceRaised)
                      .border(1.dp, RivoraColor.BorderDefault, CircleShape)
                      .clickable { onCancelActive() }
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = "✕",
                      style = TextStyle(fontSize = 11.sp, color = RivoraColor.TextSecondary, fontWeight = FontWeight.Bold)
                    )
                  }
                }
              }

              // Telemetry & Size Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                val sizeText = if (activeItem.totalBytes > 0) {
                  String.format(
                    "%.1f MB of %.1f MB · %s",
                    activeMB,
                    totalMB,
                    if (cellularActive) "2 networks" else "1 network"
                  )
                } else {
                  String.format(
                    "%.1f MB · %s",
                    activeMB,
                    if (cellularActive) "2 networks" else "1 network"
                  )
                }

                Text(
                  text = sizeText,
                  style = RivoraType.MetaLine.copy(color = RivoraColor.TextSecondary),
                  modifier = Modifier.weight(1f, fill = false),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (isPaused) "PAUSED" else "$currentSpeedText $unitText",
                  style = RivoraType.rivoraText(RivoraType.Mono, 12.5f, FontWeight.Bold, 0f, 16f)
                    .copy(color = if (isPaused) RivoraColor.Amber else RivoraColor.Orange),
                  softWrap = false,
                  maxLines = 1
                )
              }

              // Progress Bar
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(999.dp))
                  .background(RivoraColor.ProgressTrack)
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (isPaused) RivoraColor.Amber else RivoraColor.Orange)
                )
              }
            }
          }
        }

        // CARDS 2: Dynamic Items in Queue (Waiting downloads)
        items(queue) { item ->
          RivoraCard(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RivoraColor.SurfaceRaised)
                    .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = item.fileExtension.ifBlank { "FILE" },
                    style = RivoraType.rivoraText(RivoraType.Mono, 10f, FontWeight.Bold, 0f, 12f)
                      .copy(color = RivoraColor.TextMuted)
                  )
                }
                Text(
                  text = item.fileName,
                  style = RivoraType.RowName.copy(
                    color = RivoraColor.TextSecondary,
                    fontWeight = FontWeight.Medium
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RivoraColor.SurfaceRaised)
                    .border(1.dp, RivoraColor.Orange.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onStartQueued(item.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "Start",
                    style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.SemiBold, 0f, 14f)
                      .copy(color = RivoraColor.OrangeBright)
                  )
                }
                Box(
                  modifier = Modifier
                    .clip(CircleShape)
                    .background(RivoraColor.SurfaceRaised)
                    .border(1.dp, RivoraColor.BorderDefault, CircleShape)
                    .clickable { onRemoveQueued(item.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "✕",
                    style = TextStyle(fontSize = 11.sp, color = RivoraColor.TextSecondary, fontWeight = FontWeight.Bold)
                  )
                }
              }
            }

            val sizeFormatted = if (item.totalBytes > 0) {
              ByteFormatter.formatBytes(item.totalBytes)
            } else {
              "Stream"
            }

            Text(
              text = "$sizeFormatted · Queued (waiting for slot)",
              style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted)
            )

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(RivoraColor.ProgressTrack)
            )
          }
        }

        // CARDS 3: Dynamic Completed Items
        items(finished) { item ->
          RivoraCard(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenFile(item) }
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RivoraColor.SurfaceRaised)
                    .border(1.dp, Color(0xFF4FD1C5).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = item.fileExtension.ifBlank { "DONE" },
                    style = RivoraType.rivoraText(RivoraType.Mono, 10f, FontWeight.Bold, 0f, 12f)
                      .copy(color = Color(0xFF4FD1C5))
                  )
                }
                Text(
                  text = item.fileName,
                  style = RivoraType.RowName.copy(
                    color = RivoraColor.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Open File Button
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x224FD1C5))
                    .border(1.dp, Color(0xFF4FD1C5), RoundedCornerShape(6.dp))
                    .clickable { onOpenFile(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "Open",
                    style = RivoraType.rivoraText(RivoraType.Sans, 11f, FontWeight.SemiBold, 0f, 14f)
                      .copy(color = Color(0xFF4FD1C5))
                  )
                }
                // Delete Button
                Box(
                  modifier = Modifier
                    .clip(CircleShape)
                    .background(RivoraColor.SurfaceRaised)
                    .border(1.dp, RivoraColor.BorderDefault, CircleShape)
                    .clickable { onDeleteFinished(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "✕",
                    style = TextStyle(fontSize = 11.sp, color = RivoraColor.TextSecondary, fontWeight = FontWeight.Bold)
                  )
                }
              }
            }

            val sizeFormatted = if (item.totalBytes > 0) {
              ByteFormatter.formatBytes(item.totalBytes)
            } else {
              "Completed"
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "$sizeFormatted · Saved to ${item.destinationPath}",
                style = RivoraType.MetaLine.copy(color = RivoraColor.TextSecondary)
              )
              Text(
                text = "100%",
                style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Bold, 0f, 14f)
                  .copy(color = Color(0xFF4FD1C5))
              )
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(RivoraColor.ProgressTrack)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .fillMaxHeight()
                  .background(Color(0xFF4FD1C5))
              )
            }
          }
        }

        // Clean Empty State: only "Click + to Download" as requested
        if (activeItem == null && queue.isEmpty() && finished.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 180.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Click + to Download",
                style = RivoraType.rivoraText(
                  family = RivoraType.Sans,
                  size = 16f,
                  weight = FontWeight.Medium,
                  tracking = 0.2f,
                  line = 22f
                ).copy(color = RivoraColor.TextMuted),
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }
    }

    // Floating Circular Action Button (+) at Bottom Right
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 22.dp, bottom = 28.dp)
        .size(58.dp)
        .clip(CircleShape)
        .background(
          brush = Brush.radialGradient(
            colors = listOf(RivoraColor.OrangeBright, RivoraColor.Orange),
            radius = 80f
          )
        )
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onNavigateToAdd
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "+",
        style = TextStyle(
          fontSize = 34.sp,
          fontWeight = FontWeight.Light,
          color = Color.White,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.offset(y = (-2).dp)
      )
    }
  }
}
