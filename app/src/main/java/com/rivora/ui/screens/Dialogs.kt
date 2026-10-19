package com.rivora.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * New Download Modal Dialog matching Screenshot 3 & UI Kit Section 9.
 * Features a tall, spacious 56dp input bar with interactive BasicTextField,
 * prominent Add action button, and authentic destination/network selectors.
 */
@Composable
fun NewDownloadDialog(
  onDismiss: () -> Unit,
  onStartDownload: () -> Unit,
  isWifiConnected: Boolean = false,
  isCellularConnected: Boolean = true
) {
  var urlInput by remember { mutableStateOf("https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso") }
  var savePath by remember { mutableStateOf("Downloads/") }

  // Folder picker contract
  val folderPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    uri?.let {
      val path = it.path ?: ""
      val friendlyName = when {
        path.contains("primary:") -> path.substringAfter("primary:") + "/"
        path.contains(":") -> path.substringAfter(":") + "/"
        else -> path
      }
      savePath = friendlyName.ifBlank { "Downloads/" }
    }
  }

  // Torrent file picker contract
  val torrentPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    uri?.let {
      urlInput = it.toString()
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(22.dp))
        .background(RivoraColor.Surface)
        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
    ) {
      Column {
        // 1. Header Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Add download",
            style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
          )
          Text(
            text = "URL · MAGNET · TORRENT",
            style = RivoraType.Eyebrow.copy(color = RivoraColor.TextMuted, fontSize = 9.sp),
            maxLines = 1,
            softWrap = false
          )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RivoraColor.BorderSubtle))

        // 2. Main Content
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Spacious 56dp Input Bar with Inset Surface
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(RivoraColor.SurfaceRaised)
              .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(14.dp))
              .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
          ) {
            BasicTextField(
              value = urlInput,
              onValueChange = { urlInput = it },
              singleLine = true,
              cursorBrush = SolidColor(RivoraColor.Orange),
              textStyle = TextStyle(
                fontFamily = RivoraType.Mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = RivoraColor.TextPrimary
              ),
              modifier = Modifier.fillMaxWidth(),
              decorationBox = { innerTextField ->
                if (urlInput.isEmpty()) {
                  Text(
                    text = "magnet:?xt=... or https://...",
                    style = TextStyle(
                      fontFamily = RivoraType.Mono,
                      fontSize = 13.sp,
                      color = RivoraColor.TextMuted
                    )
                  )
                }
                innerTextField()
              }
            )
          }

          // Options Row (Open Torrent)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            RivoraButton(
              text = "Open .torrent",
              onClick = { torrentPickerLauncher.launch("*/*") },
              variant = RivoraButtonVariant.DARK,
              height = 34.dp
            )
          }

          // Save Destination Row
          RivoraInsetBox(padding = 12.dp) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Save to",
                style = RivoraType.RowName.copy(color = RivoraColor.TextSecondary)
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = "📁 $savePath",
                  style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Medium, 0f, 16f).copy(color = RivoraColor.TextPrimary)
                )
                RivoraButton(
                  text = "Change",
                  onClick = { folderPickerLauncher.launch(null) },
                  variant = RivoraButtonVariant.DARK,
                  height = 28.dp
                )
              }
            }
          }

          // Network Adapters Row
          RivoraInsetBox(padding = 12.dp) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Networks",
                style = RivoraType.RowName.copy(color = RivoraColor.TextSecondary)
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isWifiConnected) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(999.dp))
                      .background(RivoraColor.OrangeDim)
                      .border(1.dp, RivoraColor.OrangeBorder, RoundedCornerShape(999.dp))
                      .padding(horizontal = 10.dp, vertical = 4.dp)
                  ) {
                    Text(
                      text = "● Wi-Fi",
                      color = RivoraColor.OrangeBright,
                      style = RivoraType.LivePill
                    )
                  }
                }
                if (isCellularConnected) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(999.dp))
                      .background(RivoraColor.StreamSecondaryDim)
                      .border(1.dp, RivoraColor.StreamSecondaryBorder, RoundedCornerShape(999.dp))
                      .padding(horizontal = 10.dp, vertical = 4.dp)
                  ) {
                    Text(
                      text = "● Cellular",
                      color = RivoraColor.StreamSecondary,
                      style = RivoraType.LivePill
                    )
                  }
                }
                if (!isWifiConnected && !isCellularConnected) {
                  Text(
                    text = "No connection",
                    style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted)
                  )
                }
              }
            }
          }
        }

        // 3. Action Footer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(RivoraColor.DialogFooter)
            .border(1.dp, RivoraColor.BorderSubtle)
            .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          RivoraButton(
            text = "Cancel",
            onClick = onDismiss,
            variant = RivoraButtonVariant.DARK,
            height = 38.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          RivoraButton(
            text = "Add Download",
            onClick = onStartDownload,
            variant = RivoraButtonVariant.AMBER,
            height = 38.dp
          )
        }
      }
    }
  }
}

/**
 * Active Networks Popover Modal
 */
@Composable
fun NetworksDialog(
  cellularActive: Boolean,
  onDismiss: () -> Unit,
  onOpenLimits: () -> Unit,
  isWifiConnected: Boolean = false,
  isCellularConnected: Boolean = true
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .clip(RoundedCornerShape(22.dp))
        .background(RivoraColor.Surface)
        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
    ) {
      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "Active Networks", style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary))
          Text(
            text = "✕",
            color = RivoraColor.TextSecondary,
            modifier = Modifier.clickable { onDismiss() }
          )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RivoraColor.BorderSubtle))

        // Wi-Fi Item (Only if connected)
        if (isWifiConnected) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              NetworkDot(
                color = RivoraColor.Orange,
                size = 12.dp
              )
              Text(
                text = "Wi-Fi",
                style = RivoraType.RowName.copy(
                  color = RivoraColor.TextPrimary,
                  fontWeight = FontWeight.SemiBold
                )
              )
            }
            Text(
              text = "Connected ›",
              style = RivoraType.LivePill.copy(
                color = RivoraColor.OrangeBright
              )
            )
          }
        }

        if (isWifiConnected && isCellularConnected) {
          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RivoraColor.BorderSubtle))
        }

        // Cellular Item (Only if connected)
        if (isCellularConnected) {
          val isCellActive = cellularActive
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              NetworkDot(
                color = if (isCellActive) RivoraColor.StreamSecondary else RivoraColor.TextMuted,
                size = 12.dp
              )
              Text(
                text = "Cellular",
                style = RivoraType.RowName.copy(
                  color = RivoraColor.TextPrimary,
                  fontWeight = FontWeight.SemiBold
                )
              )
            }
            Text(
              text = if (isCellActive) "Connected ›" else "Standby ›",
              style = RivoraType.LivePill.copy(
                color = if (isCellActive) RivoraColor.StreamSecondary else RivoraColor.TextMuted
              )
            )
          }
        }

        if (!isWifiConnected && !isCellularConnected) {
          Text(
            text = "No active network interfaces detected",
            style = RivoraType.MetaLine.copy(color = RivoraColor.TextMuted),
            modifier = Modifier.padding(20.dp)
          )
        }

        // Limits Button
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLimits() }
            .background(RivoraColor.DialogFooter)
            .border(1.dp, RivoraColor.BorderSubtle)
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "Speed & data limits", style = RivoraType.ButtonLabel.copy(color = RivoraColor.Orange))
        }
      }
    }
  }
}

/**
 * Speed & Data Limits Dialog matching Screenshot 4 & UI Kit Section 10.
 * Features limited network card with speed cap slider, daily data budget,
 * stop-at-limit switch, and Wi-Fi no-limits summary.
 */
@Composable
fun LimitsDialog(onDismiss: () -> Unit) {
  var speedCapMBps by remember { mutableFloatStateOf(8f) }
  var stopAtLimit by remember { mutableStateOf(true) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .clip(RoundedCornerShape(22.dp))
        .background(RivoraColor.Surface)
        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Close Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Speed & Data Limits",
            style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
          )
          Text(
            text = "✕",
            color = RivoraColor.TextSecondary,
            modifier = Modifier.clickable { onDismiss() }
          )
        }

        // 1. Limited Network Card (Cellular / USB Tethering) with Ambient Orange Aura
        RivoraCard(hasAura = true) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NetworkDot(color = RivoraColor.StreamSecondary, size = 12.dp)
              Text(
                text = "Cellular Limits",
                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
              )
            }
            Text(
              text = "LIMITS ON",
              style = RivoraType.Eyebrow.copy(color = RivoraColor.OrangeBright)
            )
          }

          // Tile 1: Speed Cap Slider
          RivoraInsetBox(padding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Speed cap",
                  style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                )
                Text(
                  text = "${speedCapMBps.toInt()} MB/s",
                  style = RivoraType.rivoraText(RivoraType.Mono, 13.5f, FontWeight.Bold, 0f, 17f).copy(color = RivoraColor.TextPrimary)
                )
              }

              Slider(
                value = speedCapMBps,
                onValueChange = { speedCapMBps = it },
                valueRange = 0f..20f,
                colors = SliderDefaults.colors(
                  thumbColor = RivoraColor.White,
                  activeTrackColor = RivoraColor.White,
                  inactiveTrackColor = RivoraColor.ProgressTrack
                )
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "0",
                  style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(color = RivoraColor.TextMuted)
                )
                Text(
                  text = "20 MB/s",
                  style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 14f).copy(color = RivoraColor.TextMuted)
                )
              }
            }
          }

          // Tile 2: Daily Data Budget & Stop At Limit
          RivoraInsetBox(padding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Daily data budget",
                  style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary)
                )
                Text(
                  text = "1.2 / 2.0 GB",
                  style = RivoraType.rivoraText(RivoraType.Mono, 13.5f, FontWeight.Bold, 0f, 17f).copy(color = RivoraColor.TextPrimary)
                )
              }

              // Dual-Color Progress Bar (White used + Orange remainder)
              LargeProgressBar(
                progress = 0.60f,
                secondaryProgress = 0.40f,
                height = 7.dp
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Stop at limit",
                  style = RivoraType.RowName.copy(color = RivoraColor.TextSecondary)
                )
                RivoraSwitch(
                  checked = stopAtLimit,
                  onCheckedChange = { stopAtLimit = it },
                  activeTrackColor = RivoraColor.Orange
                )
              }
            }
          }
        }

        // 2. Wi-Fi Card: No Limits Summary matching Screenshot 4
        RivoraCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NetworkDot(color = RivoraColor.Orange, size = 12.dp)
              Text(
                text = "Wi-Fi",
                style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
              )
            }
            Text(
              text = "NO LIMITS",
              style = RivoraType.Eyebrow.copy(color = RivoraColor.TextMuted)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // Speed Cap Tile
            RivoraInsetBox(modifier = Modifier.weight(1f), padding = 12.dp) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "SPEED CAP",
                  style = RivoraType.Eyebrow.copy(color = RivoraColor.TextMuted)
                )
                Text(
                  text = "Off",
                  style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                )
              }
            }

            // Data Limit Tile
            RivoraInsetBox(modifier = Modifier.weight(1f), padding = 12.dp) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "DATA",
                  style = RivoraType.Eyebrow.copy(color = RivoraColor.TextMuted)
                )
                Text(
                  text = "Unlimited",
                  style = RivoraType.RowName.copy(color = RivoraColor.TextPrimary, fontWeight = FontWeight.SemiBold)
                )
              }
            }
          }
        }

        // 3. Apply Action Button
        RivoraButton(
          text = "Apply Limits",
          onClick = onDismiss,
          variant = RivoraButtonVariant.AMBER,
          height = 44.dp,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}
