package com.rivora.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.FileTile
import com.rivora.ui.components.MiniStatBox
import com.rivora.ui.components.NetworkDot
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraInsetBox
import com.rivora.ui.custom.ChunkMatrixCanvas
import com.rivora.util.ByteFormatter

@Composable
fun ChunksTab(
  completedChunks: Int,
  cellularActive: Boolean,
  isPaused: Boolean,
  totalChunks: Int = 200,
  fileName: String = "",
  fileExt: String = "BIN",
  downloadedBytes: Long = 0L,
  totalBytes: Long = 0L,
  currentSpeedMBps: Float = 0f,
  droppedCount: Int = 0,
  etaText: String = "",
  modifier: Modifier = Modifier
) {
  val progressPercent = if (totalBytes > 0) (((downloadedBytes.toFloat() / totalBytes) * 100).toInt()).coerceIn(0, 100) else 0
  val isCompleted = completedChunks >= totalChunks && totalChunks > 0 || (totalBytes > 0 && downloadedBytes >= totalBytes)
  val downloadedCount = completedChunks
  val remainingChunks = (totalChunks - completedChunks).coerceAtLeast(0)
  val statusLabel = when {
    isCompleted || progressPercent >= 100 -> "Completed"
    isPaused -> "Paused"
    etaText.isNotBlank() && etaText != "Calculating..." -> etaText
    currentSpeedMBps <= 0.05f -> "Idle"
    else -> "Calculating..."
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Active File Download Card matching Screenshot 3
    RivoraCard {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Top line: File Tile + Filename + Folder Location
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FileTile(extension = fileExt, color = Color(0xFFC0C0CC))

          Text(
            text = fileName,
            style = RivoraType.rivoraText(RivoraType.Sans, 14.5f, FontWeight.SemiBold, 0f, 18f).copy(
              color = RivoraColor.TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )

          // Folder Location Outline
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            FolderOutlineIcon(modifier = Modifier.size(13.dp), color = Color(0xFFA1A1A8))
            Text(
              text = "Downloads/",
              style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                color = Color(0xFFA1A1A8)
              ),
              maxLines = 1,
              softWrap = false
            )
          }
        }

        // Subtitle line: full width with all metadata cleanly laid out
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = ByteFormatter.formatProgress(downloadedBytes, totalBytes),
            style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
              color = Color(0xFFA1A1A8)
            ),
            maxLines = 1,
            softWrap = false
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
              color = if (isCompleted) RivoraColor.OrangeBright else RivoraColor.TextPrimary
            ),
            maxLines = 1,
            softWrap = false
          )
          Text(
            text = "·",
            style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
              color = Color(0xFFA1A1A8)
            )
          )
          Text(
            text = statusLabel,
            style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 14f).copy(
              color = if (isCompleted) RivoraColor.OrangeBright else Color(0xFFA1A1A8)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    // 2. Chunks Matrix Card matching Screenshot 3
    RivoraCard {
      // Header Legend Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Cellular legend (Amber)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5A044))
            )
            Text(
              text = if (cellularActive) "Cellular" else "Cellular (off)",
              style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 15f).copy(
                color = Color(0xFFEDEDEF)
              )
            )
          }

          // Wi-Fi legend (Teal)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFF2DD4BF))
            )
            Text(
              text = "Wi-Fi",
              style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 15f).copy(
                color = Color(0xFFEDEDEF)
              )
            )
          }
        }

        val chunkSizeText = if (totalBytes > 0 && totalChunks > 0) {
          val sizeInMb = (totalBytes.toFloat() / totalChunks) / (1024f * 1024f)
          val formatted = if (sizeInMb >= 1f) String.format("%.1f MB", sizeInMb) else String.format("%.0f KB", sizeInMb * 1024f)
          "$totalChunks chunks · $formatted each"
        } else {
          "$totalChunks chunks"
        }

        Text(
          text = chunkSizeText,
          style = RivoraType.rivoraText(RivoraType.Mono, 10.5f, FontWeight.Normal, 0f, 14f).copy(
            color = Color(0xFF8C8C94)
          ),
          maxLines = 1,
          softWrap = false
        )
      }

      // 200-Chunk Matrix Canvas
      RivoraInsetBox(padding = 10.dp) {
        ChunkMatrixCanvas(
          completedChunks = completedChunks,
          cellularActive = cellularActive,
          isPaused = isPaused,
          totalChunks = totalChunks,
          cols = 20,
          downloadedBytes = downloadedBytes,
          totalBytes = totalBytes,
          currentSpeedMBps = currentSpeedMBps
        )
      }

      // Mini Stats Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MiniStatBox(
          label = "DOWNLOADED",
          value = "$downloadedCount",
          valueColor = RivoraColor.OrangeBright,
          modifier = Modifier.weight(1f)
        )
        MiniStatBox(
          label = "DROPPED",
          value = "$droppedCount",
          valueColor = Color(0xFFD4D4D8),
          modifier = Modifier.weight(1f)
        )
        MiniStatBox(
          label = "REMAINING",
          value = "$remainingChunks",
          modifier = Modifier.weight(1f)
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
