package com.rivora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.data.DownloadItem
import com.rivora.data.DownloadStatus
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.NetworkDot
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import com.rivora.ui.components.RivoraCheckbox
import com.rivora.ui.components.RivoraInsetBox

/**
 * Downloads Screen matching Screenshot 3 & UI Kit Section 9.
 * Integrates the authentic Add Download panel with spacious input bar and Add action,
 * and the Queue panel with File/Torrent badges, dual progress styles, and subtle orange ambient glow.
 */
@Composable
fun DownloadsListScreen(
  activeItem: DownloadItem,
  finishedItem: DownloadItem,
  currentSpeedText: String,
  unitText: String,
  cellularActive: Boolean,
  onOpenItem: (DownloadItem) -> Unit,
  onOpenNetworks: () -> Unit,
  onOpenNewDownload: () -> Unit,
  modifier: Modifier = Modifier
) {
  val progress = if (activeItem.totalBytes > 0) (activeItem.downloadedBytes.toFloat() / activeItem.totalBytes).coerceIn(0f, 1f) else 0f
  var inlineUrlInput by remember { mutableStateOf("magnet:?xt=urn:btih:3b245504cf5f11bbdbe1201...") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(RivoraColor.App)
  ) {
    // 1. Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "All downloads ▾",
        style = RivoraType.ListTitle.copy(color = RivoraColor.TextPrimary)
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Network Status Button
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RivoraColor.Surface)
            .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(8.dp))
            .clickable { onOpenNetworks() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          NetworkDot(color = RivoraColor.Orange, size = 10.dp)
          Text(
            text = if (cellularActive) "2 networks ▾" else "1 network ▾",
            style = RivoraType.ButtonLabel.copy(color = RivoraColor.TextPrimary)
          )
        }

        // New Download Action Button
        RivoraButton(
          text = "+ New download",
          onClick = onOpenNewDownload,
          variant = RivoraButtonVariant.AMBER,
          height = 34.dp
        )
      }
    }

    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RivoraColor.BorderSubtle))

    // 2. Scrollable Cards matching Screenshot 3
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Card 1: Add Download Panel matching Screenshot 3
      item {
        RivoraCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Add download",
              style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
            )
            Text(
              text = "URL · MAGNET · .TORRENT",
              style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
            )
          }

          // Spacious 52dp Input Bar with integrated Add button
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(RivoraColor.SurfaceRaised)
              .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(14.dp))
              .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            BasicTextField(
              value = inlineUrlInput,
              onValueChange = { inlineUrlInput = it },
              singleLine = true,
              cursorBrush = SolidColor(RivoraColor.Orange),
              textStyle = TextStyle(
                fontFamily = RivoraType.Mono,
                fontSize = 12.5.sp,
                color = RivoraColor.TextPrimary
              ),
              modifier = Modifier.weight(1f),
              decorationBox = { innerTextField ->
                if (inlineUrlInput.isEmpty()) {
                  Text(
                    text = "magnet:?xt=... or https://...",
                    style = TextStyle(
                      fontFamily = RivoraType.Mono,
                      fontSize = 12.5.sp,
                      color = RivoraColor.TextMuted
                    )
                  )
                }
                innerTextField()
              }
            )

            RivoraButton(
              text = "Add",
              onClick = onOpenNewDownload,
              variant = RivoraButtonVariant.AMBER,
              height = 40.dp
            )
          }
        }
      }

      // Card 2: Queue Panel matching Screenshot 3
      item {
        RivoraCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Queue",
              style = RivoraType.headingCard.copy(color = RivoraColor.TextPrimary)
            )
            Text(
              text = "2 DOWNLOADING",
              style = RivoraType.Eyebrow.copy(color = RivoraColor.TextSecondary)
            )
          }

          // Item 1: Active File (Orange progress)
          QueueItemRow(
            badgeText = "FILE",
            isTorrent = false,
            title = activeItem.fileName,
            percentText = "${(progress * 100).toInt()}%",
            subtitle = String.format("%.1f GB of %.1f GB · %s",
              activeItem.downloadedBytes / (1024f*1024f*1024f),
              activeItem.totalBytes / (1024f*1024f*1024f),
              if (cellularActive) "2 networks" else "1 network"
            ),
            progress = progress,
            barColor = RivoraColor.Orange,
            onClick = { onOpenItem(activeItem) }
          )

          // Item 2: Finished / Aggregating Torrent (Silver progress)
          QueueItemRow(
            badgeText = "TORRENT",
            isTorrent = true,
            title = finishedItem.fileName,
            percentText = "34%",
            subtitle = "2.0 GB of 5.8 GB · 48 peers · 2 networks",
            progress = 0.34f,
            barColor = RivoraColor.StreamSecondary,
            onClick = { onOpenItem(finishedItem) }
          )

          // Item 3: Queued File (Muted, no progress)
          QueueItemRow(
            badgeText = "FILE",
            isTorrent = false,
            title = "Footage_Final_4K.mov",
            percentText = "Queued",
            subtitle = "3.4 GB · waiting",
            progress = 0f,
            barColor = Color.Transparent,
            isQueued = true,
            onClick = {}
          )
        }
      }
    }

    // 3. Bottom Summary Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(RivoraColor.ListGroupBar)
        .border(1.dp, RivoraColor.BorderSubtle)
        .padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "3 items total",
        style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.Normal, 0f, 15f).copy(color = RivoraColor.TextSecondary)
      )

      RivoraButton(
        text = "View Active Download →",
        onClick = { onOpenItem(activeItem) },
        variant = RivoraButtonVariant.DARK,
        height = 32.dp
      )
    }
  }
}

@Composable
private fun QueueItemRow(
  badgeText: String,
  isTorrent: Boolean,
  title: String,
  percentText: String,
  subtitle: String,
  progress: Float,
  barColor: Color,
  isQueued: Boolean = false,
  onClick: () -> Unit
) {
  RivoraInsetBox(padding = 14.dp) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() },
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isTorrent) RivoraColor.OrangeDim else Color(0xFF282830))
            .border(1.dp, if (isTorrent) RivoraColor.OrangeBorder else Color(0xFF383844), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = badgeText,
            style = RivoraType.rivoraText(RivoraType.Mono, 9.5f, FontWeight.SemiBold, 0.4f, 12f).copy(
              color = if (isTorrent) RivoraColor.OrangeBright else Color(0xFFC0C0CC)
            )
          )
        }

        Text(
          text = title,
          style = RivoraType.RowName.copy(
            color = if (isQueued) RivoraColor.TextSecondary else RivoraColor.TextPrimary,
            fontWeight = FontWeight.Medium
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )

        Text(
          text = percentText,
          style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.SemiBold, 0f, 15f).copy(
            color = if (isQueued) RivoraColor.TextMuted else RivoraColor.TextSecondary
          )
        )
      }

      Text(
        text = subtitle,
        style = RivoraType.RowSub.copy(color = RivoraColor.TextMuted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      if (progress > 0f) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(RivoraColor.ProgressTrack)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(progress.coerceIn(0f, 1f))
              .fillMaxHeight()
              .clip(RoundedCornerShape(999.dp))
              .background(barColor)
          )
        }
      }
    }
  }
}
