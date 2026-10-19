package com.rivora.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.NetworkDot
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraCheckbox
import com.rivora.ui.custom.SpeedMergerCanvas
import com.rivora.util.ByteFormatter

/**
 * Overview / Transfer Dashboard matching the official Rivora webapp (rivora.app)
 * with dedicated Combined Speed Merger, Active Download Card, and Full-Width Network Loading Flow.
 */
@Composable
fun OverviewTab(
  fileName: String,
  fileExt: String,
  downloadedBytes: Long,
  totalBytes: Long,
  currentSpeedText: String,
  unitText: String,
  avgSpeedText: String,
  peakSpeedText: String,
  wifiSpeedText: String,
  cellularSpeedText: String,
  cellularActive: Boolean,
  completedChunks: Int,
  totalChunks: Int,
  wifiStreams: Int,
  isPaused: Boolean,
  etaText: String,
  history: List<Float> = emptyList(),
  onCancel: () -> Unit,
  onTogglePause: () -> Unit,
  onShowInFolder: () -> Unit,
  onOpenFile: () -> Unit = {},
  modifier: Modifier = Modifier,
  wifiInterface: String = "wlan0",
  cellularInterface: String = "rmnet0",
  activeNetworksCount: Int = 2,
  isWifiConnected: Boolean = false,
  isCellularConnected: Boolean = true
) {
  val isCompleted = (totalBytes > 0 && downloadedBytes >= totalBytes) || (completedChunks >= totalChunks && totalChunks > 0)
  val progress = if (isCompleted) 1f else if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
  val progressPercent = if (isCompleted) 100 else (progress * 100).toInt()

  val isCellActive = isCellularConnected && cellularActive
  val isDual = isWifiConnected && isCellActive

  val wifiSpd = if (isWifiConnected) (wifiSpeedText.toFloatOrNull() ?: 0f) else 0f
  val cellSpd = if (isCellActive) (cellularSpeedText.toFloatOrNull() ?: 0f) else 0f
  val totalSpd = wifiSpd + cellSpd

  val targetWifiRatio: Float
  val targetCellRatio: Float
  val wifiSharePercent: Int
  val cellSharePercent: Int

  when {
    isDual && totalSpd > 0.05f -> {
      targetWifiRatio = (wifiSpd / totalSpd).coerceIn(0f, 1f)
      targetCellRatio = (cellSpd / totalSpd).coerceIn(0f, 1f)
      wifiSharePercent = (targetWifiRatio * 100).toInt()
      cellSharePercent = 100 - wifiSharePercent
    }
    isDual -> {
      targetWifiRatio = 0.5f
      targetCellRatio = 0.5f
      wifiSharePercent = 50
      cellSharePercent = 50
    }
    isCellActive -> {
      targetWifiRatio = 0f
      targetCellRatio = 1f
      wifiSharePercent = 0
      cellSharePercent = 100
    }
    isWifiConnected -> {
      targetWifiRatio = 1f
      targetCellRatio = 0f
      wifiSharePercent = 100
      cellSharePercent = 0
    }
    else -> {
      targetWifiRatio = 0f
      targetCellRatio = 0f
      wifiSharePercent = 0
      cellSharePercent = 0
    }
  }

  val animatedWifiRatio by animateFloatAsState(
    targetValue = targetWifiRatio,
    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
    label = "wifiRatio"
  )
  val animatedCellRatio by animateFloatAsState(
    targetValue = targetCellRatio,
    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
    label = "cellRatio"
  )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. COMBINED SPEED MERGER PANEL (Spacious Dual Stream Convergence + Total Speed)
    RivoraCard(hasAura = true) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SpeedMergerCanvas(
          wifiSpeedText = "$wifiSpeedText $unitText",
          cellularSpeedText = "$cellularSpeedText $unitText",
          isWifiConnected = isWifiConnected,
          isCellularConnected = isCellularConnected,
          cellularActive = cellularActive,
          isPaused = isPaused,
          secondaryLabel = if (cellularInterface.contains("usb", ignoreCase = true)) "USB" else "CELL",
          modifier = Modifier.weight(1.02f)
        )

        Column(
          modifier = Modifier
            .weight(0.98f)
            .padding(end = 4.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text(
            text = "TOTAL SPEED",
            style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.SemiBold, 1.4f, 14f).copy(
              color = Color(0xFFA1A1A8)
            )
          )

          Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
          ) {
            Text(
              text = currentSpeedText,
              style = RivoraType.HeroSpeed.copy(
                fontSize = 36.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
              ),
              softWrap = false,
              maxLines = 1
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = unitText,
              style = RivoraType.HeroUnit.copy(
                fontSize = 14.sp,
                color = Color(0xFFC8C8CE)
              ),
              softWrap = false,
              maxLines = 1,
              modifier = Modifier.padding(bottom = 5.dp)
            )
          }

          Text(
            text = if (activeNetworksCount == 1) "1 NETWORK ACTIVE" else "$activeNetworksCount NETWORKS ACTIVE",
            style = RivoraType.rivoraText(RivoraType.Mono, 9.5f, FontWeight.Medium, 0.8f, 12f).copy(
              color = Color(0xFFFF8A3D)
            )
          )
        }
      }
    }

    // 2. ACTIVE DOWNLOAD CARD (ZIP badge, Project.zip, 1.7 GB of 2.3 GB · 75% · 12s left, ~/Downloads, peach-orange gradient)
    RivoraCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Square File Badge
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF222225))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = fileExt.uppercase(),
            style = RivoraType.rivoraText(RivoraType.Mono, 14f, FontWeight.SemiBold, 0.6f, 16f).copy(
              color = Color(0xFFEDEDEF)
            )
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          // File Name & Folder Destination
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = fileName,
              style = RivoraType.rivoraText(RivoraType.Sans, 17f, FontWeight.SemiBold, 0f, 22f).copy(
                color = Color(0xFFEDEDEF)
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false)
            )

            // Destination Folder with outline icon
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              modifier = Modifier.padding(start = 8.dp)
            ) {
              FolderOutlineIcon(
                modifier = Modifier.size(15.dp),
                color = Color(0xFFC8C8CE)
              )
              Text(
                text = "Downloads/",
                style = RivoraType.rivoraText(RivoraType.Sans, 12.5f, FontWeight.Normal, 0f, 16f).copy(
                  color = Color(0xFFC8C8CE)
                ),
                maxLines = 1,
                softWrap = false
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Metadata row: dynamic byte format · progress% · status/ETA
          val displayStatus = when {
            isCompleted -> "Completed"
            isPaused -> "Paused"
            else -> etaText
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = ByteFormatter.formatProgress(downloadedBytes, totalBytes),
              style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
                color = Color(0xFFA1A1A8)
              ),
              softWrap = false,
              maxLines = 1
            )
            Text(
              text = "·",
              style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
                color = Color(0xFFA1A1A8)
              )
            )
            Text(
              text = "$progressPercent%",
              style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.SemiBold, 0f, 14f).copy(
                color = Color(0xFFEDEDEF)
              ),
              softWrap = false,
              maxLines = 1
            )
            Text(
              text = "·",
              style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
                color = Color(0xFFA1A1A8)
              )
            )
            Text(
              text = displayStatus,
              style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
                color = if (isCompleted) RivoraColor.Orange else Color(0xFFA1A1A8)
              ),
              softWrap = false,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      // Continuous Orange-to-Peach Gradient Progress Bar (4dp height)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp)
          .height(4.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0x14FFFFFF))
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(progress)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(
              brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFF36712), Color(0xFFFFD9BF))
              )
            )
        )
      }
    }

    // 3. ACTION BUTTONS (When completed: "Open File" and "Back to Downloads")
    if (isCompleted) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        RivoraButton(
          text = "Open File",
          onClick = onOpenFile,
          variant = RivoraButtonVariant.AMBER,
          height = 42.dp,
          modifier = Modifier.weight(1f)
        )
        RivoraButton(
          text = "Back to Downloads",
          onClick = onShowInFolder,
          variant = RivoraButtonVariant.DARK,
          height = 42.dp,
          modifier = Modifier.weight(1f)
        )
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        RivoraButton(
          text = if (isPaused) "Resume" else "Pause",
          onClick = onTogglePause,
          variant = RivoraButtonVariant.DARK,
          height = 42.dp,
          modifier = Modifier.weight(1f)
        )
        RivoraButton(
          text = "Cancel",
          onClick = onCancel,
          variant = RivoraButtonVariant.DANGER,
          height = 42.dp,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // 4. NETWORK CONTRIBUTION matching Throughput layout
    RivoraCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "NETWORK CONTRIBUTION",
          style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
        )
        Text(
          text = "SHARE",
          style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
        )
      }

      // Wi-Fi Contribution Row (Rendered ONLY if Wi-Fi is actively connected)
      if (isWifiConnected) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RivoraColor.CardInner)
            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NetworkDot(color = RivoraColor.Orange)
              RivoraCheckbox(checked = true, onCheckedChange = {})
              Text(
                text = "Wi-Fi",
                style = RivoraType.rivoraText(RivoraType.Sans, 14.5f, FontWeight.SemiBold, 0f, 18f).copy(
                  color = RivoraColor.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "$wifiSpeedText $unitText",
                style = RivoraType.rivoraText(RivoraType.Mono, 13f, FontWeight.Bold, 0f, 16f).copy(
                  color = RivoraColor.Orange
                )
              )
              Text(
                text = "· $wifiSharePercent%",
                style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.SemiBold, 0f, 15f).copy(
                  color = RivoraColor.TextSecondary
                )
              )
            }
          }

          // Full-width 6dp Progress Bar matching Throughput
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(RivoraColor.ProgressTrack)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(animatedWifiRatio)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(
                  brush = Brush.horizontalGradient(
                    colors = listOf(RivoraColor.Orange, RivoraColor.OrangeBright)
                  )
                )
            )
          }
        }
      }

      // Cellular / Secondary Contribution Row (Rendered if Cellular is connected)
      if (isCellularConnected) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RivoraColor.CardInner)
            .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NetworkDot(color = if (cellularActive) RivoraColor.StreamSecondary else RivoraColor.TextDisabled)
              RivoraCheckbox(
                checked = cellularActive,
                checkColor = RivoraColor.StreamSecondary,
                onCheckedChange = {}
              )
              Text(
                text = if (cellularInterface.contains("usb", ignoreCase = true)) "USB Tethering" else "Cellular",
                style = RivoraType.rivoraText(RivoraType.Sans, 14.5f, FontWeight.SemiBold, 0f, 18f).copy(
                  color = if (cellularActive) RivoraColor.TextPrimary else RivoraColor.TextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = if (cellularActive) "$cellularSpeedText $unitText" else "0.0 $unitText",
                style = RivoraType.rivoraText(RivoraType.Mono, 13f, FontWeight.Bold, 0f, 16f).copy(
                  color = if (cellularActive) RivoraColor.TextPrimary else RivoraColor.TextMuted
                )
              )
              Text(
                text = if (cellularActive) "· $cellSharePercent%" else "· (disabled)",
                style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.SemiBold, 0f, 15f).copy(
                  color = RivoraColor.TextSecondary
                )
              )
            }
          }

          // Full-width 6dp Progress Bar matching Throughput
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(RivoraColor.ProgressTrack)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(if (cellularActive) animatedCellRatio else 0f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(
                  brush = Brush.horizontalGradient(
                    colors = if (cellularActive) listOf(Color(0xFF0284C7), RivoraColor.StreamSecondary) else listOf(RivoraColor.TextDisabled, RivoraColor.TextDisabled)
                  )
                )
            )
          }
        }
      }

      if (!isWifiConnected && !isCellularConnected) {
        Text(
          text = "No active network interface connected",
          style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Normal, 0f, 15f).copy(color = RivoraColor.TextMuted),
          modifier = Modifier.padding(vertical = 12.dp)
        )
      }
    }
  }
}

/**
 * Clean vector outline folder icon matching the SVG from rivora.app
 */
@Composable
private fun FolderOutlineIcon(
  modifier: Modifier = Modifier,
  color: Color = Color(0xFFC8C8CE)
) {
  Canvas(modifier = modifier) {
    val strokeWidth = 1.4.dp.toPx()
    val w = size.width
    val h = size.height

    val path = Path().apply {
      moveTo(strokeWidth, h * 0.35f)
      lineTo(strokeWidth, h - strokeWidth)
      lineTo(w - strokeWidth, h - strokeWidth)
      lineTo(w - strokeWidth, h * 0.38f)
      lineTo(w * 0.52f, h * 0.38f)
      lineTo(w * 0.42f, h * 0.20f)
      lineTo(strokeWidth + 2.dp.toPx(), h * 0.20f)
      close()
    }
    drawPath(
      path = path,
      color = color,
      style = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }
}
