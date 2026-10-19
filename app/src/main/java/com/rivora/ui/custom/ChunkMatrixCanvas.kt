package com.rivora.ui.custom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rivora.theme.RivoraColor
import kotlin.math.floor

/**
 * 200-Chunk Matrix Canvas:
 * - Downloaded chunks: Solid vibrant Orange.
 * - Dropped / Error chunks: Clean White-Grey boxes.
 * - Active chunk: A SINGLE BOX that precisely fills with orange like water filling
 *   a bucket, directly proportional to bytes downloaded in this chunk.
 * - Pending chunks: Sleek dark charcoal boxes with subtle border.
 */
@Composable
fun ChunkMatrixCanvas(
  completedChunks: Int,
  cellularActive: Boolean,
  isPaused: Boolean = false,
  totalChunks: Int = 200,
  cols: Int = 20,
  downloadedBytes: Long = 0L,
  totalBytes: Long = 0L,
  currentSpeedMBps: Float = 0f,
  droppedIndices: Set<Int> = emptySet(),
  modifier: Modifier = Modifier
) {
  val safeTotalChunks = totalChunks.coerceAtLeast(1)
  val currentActiveBox = completedChunks.coerceIn(0, safeTotalChunks - 1)

  // Calculate exact byte progress inside the currently active chunk
  val chunkSizeBytes = if (safeTotalChunks > 0 && totalBytes > 0) {
    (totalBytes / safeTotalChunks).coerceAtLeast(1L)
  } else 1L

  val bytesInActiveChunk = (downloadedBytes - (completedChunks.toLong() * chunkSizeBytes))
    .coerceIn(0L, chunkSizeBytes)

  val rawFraction = if (chunkSizeBytes > 0 && totalBytes > 0) {
    (bytesInActiveChunk.toFloat() / chunkSizeBytes).coerceIn(0f, 1f)
  } else 0f

  val targetFraction = when {
    completedChunks >= safeTotalChunks -> 1f
    isPaused -> rawFraction
    else -> rawFraction
  }

  // Smooth, precise liquid-fill animation reflecting the download speed
  val animatedFillFraction by animateFloatAsState(
    targetValue = targetFraction,
    animationSpec = tween(durationMillis = 200, easing = LinearEasing),
    label = "chunkBucketFill"
  )

  val density = LocalDensity.current

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val gap = 3.dp
    val rows = (safeTotalChunks + cols - 1) / cols

    val gapPx = with(density) { gap.toPx() }
    val cellPx = floor((constraints.maxWidth - gapPx * (cols - 1)) / cols)
    val totalHeightDp = with(density) { (cellPx * rows + gapPx * (rows - 1)).toDp() }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(totalHeightDp)
    ) {
      val w = size.width
      val g = gap.toPx()
      val c = floor((w - g * (cols - 1)) / cols)
      val x0 = (w - (c * cols + g * (cols - 1))) / 2f
      val borderWidth = 1.2.dp.toPx()
      val cornerRadius = CornerRadius(2.5.dp.toPx())

      for (i in 0 until safeTotalChunks) {
        val col = i % cols
        val row = i / cols
        val tl = Offset(x0 + col * (c + g), row * (c + g))
        val cellSize = Size(c, c)

        val isDropped = droppedIndices.contains(i)
        val isCompleted = i < completedChunks
        val isActive = (i == currentActiveBox) && (completedChunks < safeTotalChunks) && !isPaused

        when {
          // 1. Dropped / Error Chunks: White-Grey Box
          isDropped -> {
            drawRoundRect(
              color = Color(0xFFD4D4D8),
              topLeft = tl,
              size = cellSize,
              cornerRadius = cornerRadius
            )
            drawRoundRect(
              color = Color(0xFFE4E4E7),
              topLeft = tl + Offset(borderWidth / 2, borderWidth / 2),
              size = Size(c - borderWidth, c - borderWidth),
              cornerRadius = cornerRadius,
              style = Stroke(width = borderWidth)
            )
          }

          // 2. Fully Downloaded Chunks: Solid Vibrant Orange
          isCompleted -> {
            drawRoundRect(
              color = RivoraColor.Orange,
              topLeft = tl,
              size = cellSize,
              cornerRadius = cornerRadius
            )
            drawRoundRect(
              color = RivoraColor.OrangeBright,
              topLeft = tl + Offset(borderWidth / 2, borderWidth / 2),
              size = Size(c - borderWidth, c - borderWidth),
              cornerRadius = cornerRadius,
              style = Stroke(width = borderWidth)
            )
          }

          // 3. Single Active Chunk: Fills from bottom-up like water filling a bucket
          isActive -> {
            // Dark base background
            drawRoundRect(
              color = Color(0xFF1E1E24),
              topLeft = tl,
              size = cellSize,
              cornerRadius = cornerRadius
            )

            // Bucket water fill (bottom to top)
            val fillHeight = cellSize.height * animatedFillFraction.coerceIn(0f, 1f)
            if (fillHeight > 0f) {
              clipRect(
                left = tl.x,
                top = tl.y + cellSize.height - fillHeight,
                right = tl.x + cellSize.width,
                bottom = tl.y + cellSize.height
              ) {
                drawRoundRect(
                  color = RivoraColor.Orange,
                  topLeft = tl,
                  size = cellSize,
                  cornerRadius = cornerRadius
                )
              }
            }

            // Crisp glowing amber outline for the active chunk
            drawRoundRect(
              color = RivoraColor.OrangeBright,
              topLeft = tl + Offset(borderWidth / 2, borderWidth / 2),
              size = Size(c - borderWidth, c - borderWidth),
              cornerRadius = cornerRadius,
              style = Stroke(width = borderWidth)
            )
          }

          // 4. Pending / Empty Chunks: Dark charcoal with subtle border
          else -> {
            drawRoundRect(
              color = Color(0xFF16161A),
              topLeft = tl,
              size = cellSize,
              cornerRadius = cornerRadius
            )
            drawRoundRect(
              color = Color(0xFF26262E),
              topLeft = tl + Offset(borderWidth / 2, borderWidth / 2),
              size = Size(c - borderWidth, c - borderWidth),
              cornerRadius = cornerRadius,
              style = Stroke(width = borderWidth)
            )
          }
        }
      }
    }
  }
}
