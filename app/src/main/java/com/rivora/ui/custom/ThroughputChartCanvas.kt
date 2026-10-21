package com.rivora.ui.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rivora.theme.RivoraColor

/**
 * Compact 120dp Dual-Stream Throughput Waveform Canvas matching Screenshot 1 & UI Kit Section 7 A2.
 * Renders the authentic stacked dual-series: deep orange lower layer + neutral gray upper layer
 * with crisp off-white top contour stroke.
 */
@Composable
fun ThroughputChartCanvas(
  history: List<Float>,
  averageSpeed: Float,
  maxAxisValue: Float,
  unitText: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(110.dp)
      .clip(RoundedCornerShape(8.dp))
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Subtle horizontal grid lines matching SVG <line class="gline">
      val gridY1 = h * 0.33f
      val gridY2 = h * 0.66f
      drawLine(
        color = Color(0x14FFFFFF),
        start = Offset(0f, gridY1),
        end = Offset(w, gridY1),
        strokeWidth = 1.dp.toPx()
      )
      drawLine(
        color = Color(0x14FFFFFF),
        start = Offset(0f, gridY2),
        end = Offset(w, gridY2),
        strokeWidth = 1.dp.toPx()
      )

      if (history.size < 2) return@Canvas

      // Dynamically calculate chart ceiling from data with generous headroom so peaks and valleys are pronounced
      val dataPeak = history.maxOrNull() ?: 1f
      val chartCeiling = maxOf(dataPeak * 1.25f, 0.2f)
      val count = history.size

      val totalPoints = mutableListOf<Offset>()
      val wifiPoints = mutableListOf<Offset>()

      for (i in 0 until count) {
        val speed = history[i]
        // Normalize speed relative to chart ceiling: 0 -> bottom (h), chartCeiling -> top (0.10 * h)
        val ratio = if (speed > 0.05f) (speed / chartCeiling).coerceIn(0.02f, 0.95f) else 0f
        val wifiNorm = if (speed > 0.05f) ratio * 0.72f else 0f

        val x = (i.toFloat() / (count - 1)) * w
        val yTotal = h - (ratio * h * 0.90f)
        val yWifi = h - (wifiNorm * h * 0.90f)

        totalPoints.add(Offset(x, yTotal))
        wifiPoints.add(Offset(x, yWifi))
      }

      // 1. Lower Series: Wi-Fi warm terracotta orange area with smooth cubic curves
      val wifiAreaPath = Path().apply {
        moveTo(wifiPoints.first().x, h)
        lineTo(wifiPoints.first().x, wifiPoints.first().y)
        for (i in 1 until wifiPoints.size) {
          val prev = wifiPoints[i - 1]
          val curr = wifiPoints[i]
          val cX = (prev.x + curr.x) / 2f
          cubicTo(cX, prev.y, cX, curr.y, curr.x, curr.y)
        }
        lineTo(wifiPoints.last().x, h)
        close()
      }
      drawPath(
        path = wifiAreaPath,
        color = Color(0xB8A84310) // Warm terracotta orange rgba(243, 103, 18, 0.65)
      )

      // 2. Upper Series: Neutral Silver/Gray Area Fill (USB/Cellular stacked above Wi-Fi)
      val grayAreaPath = Path().apply {
        moveTo(totalPoints.first().x, totalPoints.first().y)
        for (i in 1 until totalPoints.size) {
          val prev = totalPoints[i - 1]
          val curr = totalPoints[i]
          val cX = (prev.x + curr.x) / 2f
          cubicTo(cX, prev.y, cX, curr.y, curr.x, curr.y)
        }
        lineTo(wifiPoints.last().x, wifiPoints.last().y)
        for (i in (wifiPoints.size - 1) downTo 1) {
          val curr = wifiPoints[i]
          val prev = wifiPoints[i - 1]
          val cX = (curr.x + prev.x) / 2f
          cubicTo(cX, curr.y, cX, prev.y, prev.x, prev.y)
        }
        close()
      }
      drawPath(
        path = grayAreaPath,
        color = Color(0x80C8C8CE) // Gray fill rgba(200, 200, 206, 0.50)
      )

      // 3. Top Boundary: Crisp Off-White Contour Line (.cline)
      val topStrokePath = Path().apply {
        moveTo(totalPoints.first().x, totalPoints.first().y)
        for (i in 1 until totalPoints.size) {
          val prev = totalPoints[i - 1]
          val curr = totalPoints[i]
          val cX = (prev.x + curr.x) / 2f
          cubicTo(cX, prev.y, cX, curr.y, curr.x, curr.y)
        }
      }
      drawPath(
        path = topStrokePath,
        color = Color(0xFFEDEDEF),
        style = Stroke(
          width = 2.dp.toPx(),
          cap = StrokeCap.Round,
          join = StrokeJoin.Round
        )
      )
    }
  }
}
