package com.rivora.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.LargeProgressBar
import com.rivora.ui.components.MiniStatBox
import com.rivora.ui.components.NetworkDot
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraInsetBox
import com.rivora.ui.custom.ThroughputChartCanvas

/**
 * Throughput Telemetry & Interface Contribution Screen.
 * Uses compact 120dp waveform chart and expands the Interface Contribution section into a rich,
 * spacious panel matching Screenshot 1 & 2.
 */
@Composable
fun ThroughputTab(
  currentSpeedText: String,
  unitText: String,
  wifiSpeedText: String,
  cellularSpeedText: String,
  cellularActive: Boolean,
  avgSpeedText: String,
  peakSpeedText: String,
  history: List<Float>,
  maxAxisValue: Float,
  wifiStreams: Int,
  modifier: Modifier = Modifier,
  cellularStreams: Int = 0,
  wifiInterface: String = "wlan0",
  cellularInterface: String = "rmnet0",
  isWifiConnected: Boolean = false,
  isCellularConnected: Boolean = true
) {
  val isCellActive = isCellularConnected && cellularActive
  val isDual = isWifiConnected && isCellActive

  val wifiSpd = if (isWifiConnected) (wifiSpeedText.toFloatOrNull() ?: 0f) else 0f
  val cellSpd = if (isCellActive) (cellularSpeedText.toFloatOrNull() ?: 0f) else 0f
  val totalSpd = wifiSpd + cellSpd

  val wifiRatio: Float
  val cellRatio: Float
  val wifiSharePercent: Int
  val cellSharePercent: Int

  when {
    isDual && totalSpd > 0.05f -> {
      wifiRatio = (wifiSpd / totalSpd).coerceIn(0f, 1f)
      cellRatio = (cellSpd / totalSpd).coerceIn(0f, 1f)
      wifiSharePercent = (wifiRatio * 100).toInt()
      cellSharePercent = 100 - wifiSharePercent
    }
    isDual -> {
      wifiRatio = 0f
      cellRatio = 0f
      wifiSharePercent = 0
      cellSharePercent = 0
    }
    isCellActive -> {
      wifiRatio = 0f
      cellRatio = if (cellSpd > 0.05f) 1f else 0f
      wifiSharePercent = 0
      cellSharePercent = if (cellSpd > 0.05f) 100 else 0
    }
    isWifiConnected -> {
      wifiRatio = if (wifiSpd > 0.05f) 1f else 0f
      cellRatio = 0f
      wifiSharePercent = if (wifiSpd > 0.05f) 100 else 0
      cellSharePercent = 0
    }
    else -> {
      wifiRatio = 0f
      cellRatio = 0f
      wifiSharePercent = 0
      cellSharePercent = 0
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Throughput Telemetry Card with Compact 120dp Chart
    RivoraCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text(
            text = "THROUGHPUT · LAST 60S",
            style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
          )
          Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 4.dp)
          ) {
            Text(
              text = currentSpeedText,
              style = RivoraType.HeroSpeed.copy(fontSize = 32.sp, lineHeight = 36.sp, color = RivoraColor.White)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = unitText,
              style = RivoraType.HeroUnit.copy(color = RivoraColor.TextSecondary),
              modifier = Modifier.padding(bottom = 3.dp)
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          if (isWifiConnected) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              NetworkDot(color = RivoraColor.Orange)
              Text(
                text = "Wi-Fi",
                style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(color = RivoraColor.TextSecondary)
              )
              Text(
                text = "$wifiSpeedText $unitText",
                style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.SemiBold, 0f, 15f).copy(color = RivoraColor.Orange)
              )
            }
          }

          if (isCellActive) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              NetworkDot(color = RivoraColor.StreamSecondary)
              Text(
                text = "Cellular",
                style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(color = RivoraColor.TextSecondary)
              )
              Text(
                text = "$cellularSpeedText $unitText",
                style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.SemiBold, 0f, 15f).copy(color = RivoraColor.TextPrimary)
              )
            }
          }
        }
      }

      // Compact 120dp Stacked Area Chart
      ThroughputChartCanvas(
        history = history,
        averageSpeed = avgSpeedText.toFloatOrNull() ?: 1.3f,
        maxAxisValue = maxAxisValue,
        unitText = unitText
      )

      // X-Axis Timescale (60s ago ... now)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        listOf("60s ago", "45s ago", "30s ago", "15s ago", "now").forEach { label ->
          Text(
            text = label,
            style = RivoraType.ChartAxis.copy(color = RivoraColor.TextMuted)
          )
        }
      }

      // KPI Summary Grid (Current, Average, Peak)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MiniStatBox(
          label = "CURRENT",
          value = "$currentSpeedText $unitText",
          valueColor = RivoraColor.OrangeBright,
          modifier = Modifier.weight(1f)
        )
        MiniStatBox(
          label = "AVERAGE",
          value = "$avgSpeedText $unitText",
          valueColor = RivoraColor.White,
          modifier = Modifier.weight(1f)
        )
        MiniStatBox(
          label = "PEAK (60S)",
          value = "$peakSpeedText $unitText",
          valueColor = RivoraColor.White,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // 2. ENLARGED Interface Throughput Split Card matching Reference Screenshots
    RivoraCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "INTERFACE THROUGHPUT SPLIT",
          style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
        )
        Text(
          text = "SHARE",
          style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
        )
      }

      // Wi-Fi Contribution Row (Rendered ONLY if Wi-Fi is actively connected)
      if (isWifiConnected) {
        InterfaceSplitRow(
          dotColor = RivoraColor.Orange,
          name = "Wi-Fi",
          interfaceInfo = "$wifiInterface · ${if (wifiStreams > 0) "$wifiStreams streams" else "idle"}",
          speedText = "$wifiSpeedText $unitText",
          sharePercentText = "$wifiSharePercent%",
          shareRatio = wifiRatio,
          barColor = RivoraColor.Orange
        )
      }

      // Cellular Contribution Row (Rendered if Cellular is connected)
      if (isCellularConnected) {
        InterfaceSplitRow(
          dotColor = if (cellularActive) RivoraColor.StreamSecondary else RivoraColor.TextDisabled,
          name = "Cellular",
          interfaceInfo = when {
            cellularActive -> "$cellularInterface · ${if (cellularStreams > 0) "$cellularStreams streams" else "active"}"
            else -> "$cellularInterface · disabled"
          },
          speedText = if (cellularActive) "$cellularSpeedText $unitText" else "0.0 $unitText",
          sharePercentText = if (cellularActive) "$cellSharePercent%" else "0%",
          shareRatio = if (cellularActive) cellRatio else 0f,
          barColor = if (cellularActive) RivoraColor.StreamSecondary else RivoraColor.TextDisabled
        )
      }
    }

    // 3. Combined / Dedicated Speed Card with Ambient Aura matching Screenshot 2
    RivoraCard(hasAura = true) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = when {
            isDual -> "Combined speed"
            cellularActive && isCellularConnected -> "Cellular speed"
            isWifiConnected -> "Wi-Fi speed"
            else -> "Standby"
          },
          style = RivoraType.DialogTitle.copy(color = RivoraColor.TextPrimary)
        )

        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = currentSpeedText,
            style = RivoraType.HeroSpeed.copy(fontSize = 28.sp, lineHeight = 32.sp, color = RivoraColor.White)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = unitText,
            style = RivoraType.HeroUnit.copy(color = RivoraColor.TextSecondary),
            modifier = Modifier.padding(bottom = 3.dp)
          )
        }
      }

      // Dual-Stream or Single-Stream Segmented Bar
      when {
        isDual -> LargeProgressBar(
          progress = wifiRatio,
          secondaryProgress = cellRatio,
          height = 6.dp
        )
        cellularActive && isCellularConnected -> LargeProgressBar(
          progress = 0f,
          secondaryProgress = 1f,
          height = 6.dp
        )
        isWifiConnected -> LargeProgressBar(
          progress = 1f,
          secondaryProgress = 0f,
          height = 6.dp
        )
        else -> LargeProgressBar(
          progress = 0f,
          secondaryProgress = 0f,
          height = 6.dp
        )
      }

      // Legend
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (isWifiConnected) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(RivoraColor.Orange)
            )
            Text(
              text = "WI-FI $wifiSharePercent%",
              style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Medium, 0.4f, 14f).copy(color = RivoraColor.TextSecondary)
            )
          }
        }

        if (isCellularConnected) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (cellularActive) RivoraColor.StreamSecondary else RivoraColor.TextDisabled)
            )
            Text(
              text = if (cellularActive) "CELLULAR $cellSharePercent%" else "CELLULAR (OFF)",
              style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Medium, 0.4f, 14f).copy(color = RivoraColor.TextSecondary)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InterfaceSplitRow(
  dotColor: Color,
  name: String,
  interfaceInfo: String,
  speedText: String,
  sharePercentText: String,
  shareRatio: Float,
  barColor: Color
) {
  RivoraInsetBox(padding = 14.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          NetworkDot(color = dotColor, size = 12.dp)
          Column {
            Text(
              text = name,
              style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
            )
            Text(
              text = interfaceInfo,
              style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(color = RivoraColor.TextMuted)
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = speedText,
            style = RivoraType.rivoraText(RivoraType.Mono, 14f, FontWeight.Bold, 0f, 18f).copy(
              color = if (shareRatio > 0) barColor else RivoraColor.TextMuted
            )
          )
          Text(
            text = "· $sharePercentText",
            style = RivoraType.rivoraText(RivoraType.Mono, 12.5f, FontWeight.SemiBold, 0f, 16f).copy(
              color = RivoraColor.TextSecondary
            )
          )
        }
      }

      // 6px High Progress Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(RivoraColor.ProgressTrack)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(shareRatio.coerceIn(0f, 1f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .background(barColor)
        )
      }
    }
  }
}
