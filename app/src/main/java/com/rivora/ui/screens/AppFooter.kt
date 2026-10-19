package com.rivora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.theme.rivoraSurface
import com.rivora.ui.components.RivoraSwitch

@Composable
fun AppFooter(
  freeDiskSpaceText: String,
  isSlowMode: Boolean,
  currentUnit: String,
  onToggleSlowMode: () -> Unit,
  onSelectUnit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(RivoraColor.AppFooter)
      .border(1.dp, RivoraColor.BorderSubtle)
      .navigationBarsPadding()
      .padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Disk Free Readout
    Text(
      text = freeDiskSpaceText,
      style = RivoraType.FooterText.copy(color = RivoraColor.TextSecondary),
      softWrap = false,
      maxLines = 1
    )

    // Footer Controls Row
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Upload Status Icon
      Box(
        modifier = Modifier
          .size(24.dp)
          .rivoraSurface(
            bg = Color(0xFF202020),
            border = Color(0xFF2E2E2E),
            radius = 5.dp
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(text = "↑", color = RivoraColor.TextSecondary, style = RivoraType.FooterText)
      }

      // Slow Mode Switch
      Row(
        modifier = Modifier.clickable { onToggleSlowMode() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Text(
          text = "Slow mode",
          style = RivoraType.ButtonLabel.copy(color = RivoraColor.TextSecondary),
          softWrap = false,
          maxLines = 1
        )
        RivoraSwitch(checked = isSlowMode, onCheckedChange = { onToggleSlowMode() })
      }

      // Unit Segmented Control (MB/s | Mbps)
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF222222))
          .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
      ) {
        UnitOption(
          label = "MB/s",
          isSelected = currentUnit == "MB/s",
          onClick = { onSelectUnit("MB/s") }
        )
        UnitOption(
          label = "Mbps",
          isSelected = currentUnit == "Mbps",
          onClick = { onSelectUnit("Mbps") }
        )
      }

      // Theme Icon
      Box(
        modifier = Modifier
          .size(24.dp)
          .rivoraSurface(
            bg = Color(0xFF202020),
            border = Color(0xFF2E2E2E),
            radius = 5.dp
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(text = "☾", color = RivoraColor.TextSecondary, style = RivoraType.FooterText)
      }
    }
  }
}

@Composable
private fun UnitOption(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clickable { onClick() }
      .background(if (isSelected) Color(0xFF363636) else Color.Transparent)
      .padding(horizontal = 6.dp, vertical = 3.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = RivoraType.UnitOpt.copy(
        color = if (isSelected) Color.White else RivoraColor.TextSecondary,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
      ),
      softWrap = false,
      maxLines = 1
    )
  }
}
