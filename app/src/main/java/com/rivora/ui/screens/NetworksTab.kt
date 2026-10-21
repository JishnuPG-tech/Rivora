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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.LargeProgressBar
import com.rivora.ui.components.NetworkDot
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraInsetBox
import com.rivora.ui.components.RivoraSwitch

/**
 * Networks Screen matching Screenshot 2 & UI Kit Section 8.
 * Features stacked connection rows with un-wrapped speeds, live switches,
 * and the prominent Combined Speed card with ambient orange aura.
 */
@Composable
fun NetworksTab(
  currentSpeedText: String,
  wifiSpeedText: String,
  cellularSpeedText: String,
  wifiUnitText: String = "MB/s",
  cellularUnitText: String = "MB/s",
  wifiSpeedMBps: Float = 0f,
  cellularSpeedMBps: Float = 0f,
  cellularActive: Boolean,
  unitText: String,
  wifiStreams: Int,
  downloadedGB: Float,
  onToggleCellular: () -> Unit,
  onOpenLimits: () -> Unit,
  modifier: Modifier = Modifier,
  wifiInterface: String = "wlan0",
  cellularInterface: String = "rmnet0",
  cellularStreams: Int = 0,
  isCellularConnected: Boolean = true,
  isWifiConnected: Boolean = true
) {
  val isCellActive = isCellularConnected && cellularActive
  val isDual = isWifiConnected && isCellActive

  val wifiSpd = if (isWifiConnected) wifiSpeedMBps else 0f
  val cellSpd = if (isCellActive) cellularSpeedMBps else 0f
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
      wifiRatio = 0.5f
      cellRatio = 0.5f
      wifiSharePercent = 50
      cellSharePercent = 50
    }
    isCellActive -> {
      wifiRatio = 0f
      cellRatio = 1f
      wifiSharePercent = 0
      cellSharePercent = 100
    }
    isWifiConnected -> {
      wifiRatio = 1f
      cellRatio = 0f
      wifiSharePercent = 100
      cellSharePercent = 0
    }
    else -> {
      wifiRatio = 0f
      cellRatio = 0f
      wifiSharePercent = 0
      cellSharePercent = 0
    }
  }

  val activeCount = (if (isWifiConnected) 1 else 0) + (if (isCellActive) 1 else 0)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Connections Panel
    RivoraCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Connections",
          style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
        )
        val activeCount = (if (isWifiConnected) 1 else 0) + (if (isCellActive) 1 else 0)
        val totalAvailable = (if (isWifiConnected) 1 else 0) + (if (isCellularConnected) 1 else 0)
        Text(
          text = if (totalAvailable > 1) "$activeCount OF $totalAvailable ACTIVE" else "$activeCount ACTIVE",
          style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
        )
      }

      // Connection Row 1: Wi-Fi (Only displayed if Wi-Fi is connected)
      if (isWifiConnected) {
        ConnectionRowItem(
          dotColor = RivoraColor.Orange,
          title = "Wi-Fi",
          subtitle = "$wifiInterface · ${if (wifiStreams > 0) "$wifiStreams streams" else "active"}",
          valueText = wifiSpeedText,
          unitText = wifiUnitText,
          isChecked = true,
          onToggle = {},
          enabled = true,
          switchActiveColor = RivoraColor.Orange
        )
      }

      // Connection Row 2: Cellular (Only displayed if Cellular is connected)
      if (isCellularConnected) {
        ConnectionRowItem(
          dotColor = if (cellularActive) RivoraColor.StreamSecondary else RivoraColor.TextDisabled,
          title = "Cellular",
          subtitle = when {
            cellularActive -> "$cellularInterface · ${if (cellularStreams > 0) "$cellularStreams streams" else "active"}"
            else -> "$cellularInterface · disabled"
          },
          valueText = if (cellularActive) cellularSpeedText else "0.0",
          unitText = if (cellularActive) cellularUnitText else "",
          isChecked = cellularActive,
          onToggle = onToggleCellular,
          enabled = true,
          switchActiveColor = RivoraColor.StreamSecondary
        )
      }

      if (!isWifiConnected && !isCellularConnected) {
        Text(
          text = "No active network interfaces detected",
          style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted)
        )
      }
    }

    // 2. Combined / Dedicated Speed Card with Ambient Orange Aura matching Screenshot 2
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
          style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
        )

        Row {
          Text(
            text = currentSpeedText,
            style = RivoraType.HeroSpeed.copy(fontSize = 32.sp, lineHeight = 36.sp, color = RivoraColor.White),
            modifier = Modifier.alignByBaseline()
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = unitText,
            style = RivoraType.HeroUnit.copy(color = RivoraColor.TextSecondary),
            modifier = Modifier.alignByBaseline()
          )
        }
      }

      // Dual-Stream or Single-Stream Segmented Progress Bar
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

    // 3. Global Limits Action Button
    RivoraButton(
      text = "Speed & data limits",
      onClick = onOpenLimits,
      variant = RivoraButtonVariant.AMBER_OUTLINE,
      height = 44.dp,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun ConnectionRowItem(
  dotColor: Color,
  title: String,
  subtitle: String,
  valueText: String,
  unitText: String = "",
  isChecked: Boolean,
  onToggle: () -> Unit,
  enabled: Boolean = true,
  switchActiveColor: Color = RivoraColor.Orange
) {
  RivoraInsetBox(padding = 14.dp) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        NetworkDot(color = dotColor, size = 10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = title,
            style = RivoraType.rivoraText(RivoraType.Sans, 15f, FontWeight.SemiBold, 0f, 19f).copy(
              color = if (enabled) RivoraColor.TextPrimary else RivoraColor.TextDisabled
            ),
            maxLines = 1,
            softWrap = false
          )
          Text(
            text = subtitle,
            style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
              color = if (enabled) RivoraColor.TextMuted else RivoraColor.TextDisabled
            ),
            maxLines = 1,
            softWrap = false
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier.width(62.dp),
          contentAlignment = Alignment.CenterEnd
        ) {
          if (valueText == "—") {
            Text(
              text = "—",
              style = RivoraType.rivoraText(RivoraType.Mono, 13.5f, FontWeight.Normal, 0f, 17f).copy(
                color = RivoraColor.TextMuted,
                textAlign = TextAlign.End
              ),
              modifier = Modifier.padding(end = 6.dp)
            )
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Text(
                text = valueText,
                style = RivoraType.rivoraText(RivoraType.Mono, 13.5f, FontWeight.SemiBold, 0f, 17f).copy(
                  color = if (isChecked && enabled) switchActiveColor else RivoraColor.TextMuted
                ),
                maxLines = 1,
                softWrap = false
              )
              Text(
                text = unitText,
                style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(
                  color = if (isChecked && enabled) switchActiveColor else RivoraColor.TextMuted
                ),
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }

        RivoraSwitch(
          checked = isChecked,
          onCheckedChange = { if (enabled) onToggle() },
          activeTrackColor = switchActiveColor,
          enabled = enabled
        )
      }
    }
  }
}
