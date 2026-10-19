package com.rivora.ui.custom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType

/**
 * Adaptive Speed Merger Canvas supporting Single-Stream (Cellular-only or Wi-Fi-only)
 * and Dual-Stream Convergence based on actual real-time Android device connectivity.
 */
@Composable
fun SpeedMergerCanvas(
  wifiSpeedText: String,
  cellularSpeedText: String,
  isWifiConnected: Boolean,
  isCellularConnected: Boolean,
  cellularActive: Boolean,
  isPaused: Boolean = false,
  secondaryLabel: String = "CELL",
  modifier: Modifier = Modifier
) {
  val isCellActive = isCellularConnected && cellularActive
  val isDual = isWifiConnected && isCellActive

  val transition = rememberInfiniteTransition(label = "dash")
  val phase by transition.animateFloat(
    initialValue = 0f,
    targetValue = -30f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "phase"
  )

  val textMeasurer = rememberTextMeasurer()

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(110.dp)
  ) {
    val density = this.density
    val w = size.width
    val h = size.height

    // Geometric Anchors
    val nodeX = (w * 0.44f).coerceIn(58.dp.toPx(), 74.dp.toPx())
    val convX = (w * 0.75f).coerceIn(nodeX + 36.dp.toPx(), w - 32.dp.toPx())
    val convY = h * 0.52f

    if (isDual) {
      // -------------------------------------------------------------
      // DUAL CONVERGENCE: Wi-Fi (top) + Cellular (bottom) -> Conv
      // -------------------------------------------------------------
      val wifiY = h * 0.32f
      val cellY = h * 0.72f

      // 1. Wi-Fi
      val wifiLabelLayout = textMeasurer.measure(
        text = "WI-FI",
        style = RivoraType.rivoraText(RivoraType.Mono, 9.5f, FontWeight.Medium, 0.6f, 13f).copy(color = RivoraColor.TextSecondary)
      )
      drawText(
        textLayoutResult = wifiLabelLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - wifiLabelLayout.size.width, wifiY - 18.dp.toPx())
      )

      val wifiSpeedLayout = textMeasurer.measure(
        text = wifiSpeedText,
        style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Bold, 0f, 16f).copy(color = RivoraColor.Orange)
      )
      drawText(
        textLayoutResult = wifiSpeedLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - wifiSpeedLayout.size.width, wifiY - 3.dp.toPx())
      )

      drawCircle(
        color = RivoraColor.Orange.copy(alpha = 0.28f),
        radius = 7.dp.toPx(),
        center = Offset(nodeX, wifiY)
      )
      drawCircle(
        color = RivoraColor.Orange,
        radius = 4.dp.toPx(),
        center = Offset(nodeX, wifiY)
      )

      val wifiPath = Path().apply {
        moveTo(nodeX + 4.dp.toPx(), wifiY)
        cubicTo(
          nodeX + (convX - nodeX) * 0.45f, wifiY,
          nodeX + (convX - nodeX) * 0.55f, convY,
          convX, convY
        )
      }
      drawPath(
        path = wifiPath,
        color = RivoraColor.Orange,
        style = Stroke(
          width = 2.4.dp.toPx(),
          cap = StrokeCap.Round,
          pathEffect = if (!isPaused) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()), phase * density) else null
        )
      )

      // 2. Cellular
      val cellLabelLayout = textMeasurer.measure(
        text = secondaryLabel,
        style = RivoraType.rivoraText(RivoraType.Mono, 9.5f, FontWeight.Medium, 0.5f, 13f).copy(color = RivoraColor.TextSecondary)
      )
      drawText(
        textLayoutResult = cellLabelLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - cellLabelLayout.size.width, cellY - 18.dp.toPx())
      )

      val cellSpeedLayout = textMeasurer.measure(
        text = cellularSpeedText,
        style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Medium, 0f, 16f).copy(color = RivoraColor.StreamSecondary)
      )
      drawText(
        textLayoutResult = cellSpeedLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - cellSpeedLayout.size.width, cellY - 3.dp.toPx())
      )

      drawCircle(
        color = RivoraColor.StreamSecondary.copy(alpha = 0.28f),
        radius = 7.dp.toPx(),
        center = Offset(nodeX, cellY)
      )
      drawCircle(
        color = RivoraColor.StreamSecondary,
        radius = 4.dp.toPx(),
        center = Offset(nodeX, cellY)
      )

      val cellPath = Path().apply {
        moveTo(nodeX + 4.dp.toPx(), cellY)
        cubicTo(
          nodeX + (convX - nodeX) * 0.45f, cellY,
          nodeX + (convX - nodeX) * 0.55f, convY,
          convX, convY
        )
      }
      drawPath(
        path = cellPath,
        color = RivoraColor.StreamSecondary,
        style = Stroke(
          width = 2.0.dp.toPx(),
          cap = StrokeCap.Round,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()), if (!isPaused) phase * density else 0f)
        )
      )

    } else if (isCellActive) {
      // -------------------------------------------------------------
      // CELLULAR-ONLY: Single centered horizontal stream straight to arrow
      // -------------------------------------------------------------
      val cellLabelLayout = textMeasurer.measure(
        text = secondaryLabel,
        style = RivoraType.rivoraText(RivoraType.Mono, 9.5f, FontWeight.Medium, 0.5f, 13f).copy(color = RivoraColor.TextSecondary)
      )
      drawText(
        textLayoutResult = cellLabelLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - cellLabelLayout.size.width, convY - 18.dp.toPx())
      )

      val cellSpeedLayout = textMeasurer.measure(
        text = cellularSpeedText,
        style = RivoraType.rivoraText(RivoraType.Mono, 13f, FontWeight.Bold, 0f, 16f).copy(color = RivoraColor.StreamSecondary)
      )
      drawText(
        textLayoutResult = cellSpeedLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - cellSpeedLayout.size.width, convY - 3.dp.toPx())
      )

      drawCircle(
        color = RivoraColor.StreamSecondary.copy(alpha = 0.28f),
        radius = 7.dp.toPx(),
        center = Offset(nodeX, convY)
      )
      drawCircle(
        color = RivoraColor.StreamSecondary,
        radius = 4.dp.toPx(),
        center = Offset(nodeX, convY)
      )

      // Straight flow path to convergence
      drawLine(
        color = RivoraColor.StreamSecondary,
        start = Offset(nodeX + 4.dp.toPx(), convY),
        end = Offset(convX, convY),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = if (!isPaused) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()), phase * density) else null
      )

    } else if (isWifiConnected) {
      // -------------------------------------------------------------
      // WI-FI-ONLY: Single centered horizontal stream straight to arrow
      // -------------------------------------------------------------
      val wifiLabelLayout = textMeasurer.measure(
        text = "WI-FI",
        style = RivoraType.rivoraText(RivoraType.Mono, 9.5f, FontWeight.Medium, 0.6f, 13f).copy(color = RivoraColor.TextSecondary)
      )
      drawText(
        textLayoutResult = wifiLabelLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - wifiLabelLayout.size.width, convY - 18.dp.toPx())
      )

      val wifiSpeedLayout = textMeasurer.measure(
        text = wifiSpeedText,
        style = RivoraType.rivoraText(RivoraType.Mono, 13f, FontWeight.Bold, 0f, 16f).copy(color = RivoraColor.Orange)
      )
      drawText(
        textLayoutResult = wifiSpeedLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - wifiSpeedLayout.size.width, convY - 3.dp.toPx())
      )

      drawCircle(
        color = RivoraColor.Orange.copy(alpha = 0.28f),
        radius = 7.dp.toPx(),
        center = Offset(nodeX, convY)
      )
      drawCircle(
        color = RivoraColor.Orange,
        radius = 4.dp.toPx(),
        center = Offset(nodeX, convY)
      )

      // Straight flow path to convergence
      drawLine(
        color = RivoraColor.Orange,
        start = Offset(nodeX + 4.dp.toPx(), convY),
        end = Offset(convX, convY),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = if (!isPaused) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()), phase * density) else null
      )
    } else {
      // Offline State
      val offlineLayout = textMeasurer.measure(
        text = "OFFLINE",
        style = RivoraType.rivoraText(RivoraType.Mono, 10f, FontWeight.Medium, 0.5f, 14f).copy(color = RivoraColor.TextMuted)
      )
      drawText(
        textLayoutResult = offlineLayout,
        topLeft = Offset(nodeX - 8.dp.toPx() - offlineLayout.size.width, convY - 8.dp.toPx())
      )
      drawCircle(
        color = RivoraColor.TextMuted,
        radius = 4.dp.toPx(),
        center = Offset(nodeX, convY)
      )
    }

    // 3. Convergence Node Ring (Outer translucent ring + solid center dot)
    drawCircle(
      color = Color(0x40EDEDEF),
      radius = 7.dp.toPx(),
      center = Offset(convX, convY),
      style = Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(
      color = Color(0xFFEDEDEF),
      radius = 3.2.dp.toPx(),
      center = Offset(convX, convY)
    )

    // 4. White Flow Arrow pointing to Total Speed
    val arrowEndX = (w - 2.dp.toPx()).coerceAtLeast(convX + 18.dp.toPx())
    drawLine(
      color = Color(0xFFEDEDEF),
      start = Offset(convX + 7.dp.toPx(), convY),
      end = Offset(arrowEndX - 7.dp.toPx(), convY),
      strokeWidth = 3.5.dp.toPx(),
      cap = StrokeCap.Round
    )

    // Filled Arrowhead
    val arrowHead = Path().apply {
      moveTo(arrowEndX, convY)
      lineTo(arrowEndX - 7.dp.toPx(), convY - 4.5.dp.toPx())
      lineTo(arrowEndX - 7.dp.toPx(), convY + 4.5.dp.toPx())
      close()
    }
    drawPath(
      path = arrowHead,
      color = Color(0xFFEDEDEF)
    )
  }
}
