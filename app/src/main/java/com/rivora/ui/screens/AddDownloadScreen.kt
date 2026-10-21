package com.rivora.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.rivora.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.util.ByteFormatter
import com.rivora.ui.components.FileTile
import com.rivora.ui.components.NetworkDot
import com.rivora.ui.components.RivoraButton
import com.rivora.ui.components.RivoraButtonVariant
import com.rivora.ui.components.RivoraCard
import kotlinx.coroutines.delay

data class DetectedFileInfo(
  val name: String,
  val size: String,
  val ext: String,
  val tag: String,
  val totalBytes: Long = 0L,
  val isResumable: Boolean = true
)

@Composable
fun AddDownloadScreen(
  queueCount: Int = 0,
  cellularActive: Boolean,
  onNavigateToQueue: () -> Unit,
  onStartDownload: (url: String, fileName: String, totalBytes: Long, isResumable: Boolean, customTreeUri: Uri?, displayPath: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var urlInput by remember { mutableStateOf("") }
  var isChecking by remember { mutableStateOf(false) }
  var detectedFile by remember { mutableStateOf<DetectedFileInfo?>(null) }
  var probeError by remember { mutableStateOf<String?>(null) }
  var validationWarning by remember { mutableStateOf<String?>(null) }
  var savePath by remember { mutableStateOf("Downloads/") }
  var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
  var selectedTorrentName by remember { mutableStateOf<String?>(null) }

  val folderPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          uri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      } catch (_: Exception) {}
      selectedFolderUri = uri
      val raw = uri.lastPathSegment ?: ""
      val cleanName = raw.substringAfterLast(':').substringAfterLast('/').ifBlank { "Downloads" }
      savePath = "$cleanName/"
    }
  }

  val torrentPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      var fileName = "download.torrent"
      if (uri.scheme == "content") {
        try {
          context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
              val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (idx != -1) fileName = cursor.getString(idx)
            }
          }
        } catch (_: Exception) {}
      } else {
        fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "download.torrent"
      }

      val ext = fileName.substringAfterLast('.', "torrent").uppercase()
      selectedTorrentName = fileName
      urlInput = uri.toString()
      detectedFile = DetectedFileInfo(
        name = fileName,
        size = "Torrent",
        ext = ext,
        tag = "multi-stream torrent",
        totalBytes = 0L,
        isResumable = true
      )
    }
  }

  // Auto-check link with real backend UrlProber
  LaunchedEffect(urlInput) {
    val trimmed = urlInput.trim()
    if (trimmed.isNotBlank()) {
      isChecking = true
      detectedFile = null
      probeError = null
      validationWarning = null
      
      // Probe using real backend UrlProber
      val probe = com.rivora.backend.UrlProber.probe(trimmed)
      isChecking = false

      if (!probe.isSuccess) {
        detectedFile = null
        probeError = probe.errorMessage ?: "Link is unreachable or invalid"
        validationWarning = null
      } else if (!probe.isDownloadable) {
        detectedFile = null
        probeError = null
        validationWarning = probe.validationWarning ?: "This link points to an HTML web page rather than a downloadable file."
      } else {
        probeError = null
        validationWarning = null
        val sizeFormatted = if (probe.totalBytes > 0L) {
          ByteFormatter.formatBytes(probe.totalBytes)
        } else if (trimmed.startsWith("magnet:", ignoreCase = true) || probe.fileExtension == "TORRENT") {
          "Torrent"
        } else {
          "Stream"
        }
        detectedFile = DetectedFileInfo(
          name = probe.fileName,
          size = sizeFormatted,
          ext = probe.fileExtension,
          tag = if (probe.isResumable) "resumable · multi-stream" else "single-stream",
          totalBytes = probe.totalBytes,
          isResumable = probe.isResumable
        )
      }
    } else {
      isChecking = false
      detectedFile = null
      probeError = null
      validationWarning = null
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(RivoraColor.App)
      .padding(horizontal = 16.dp)
  ) {
    // 1. Top Bar with Queue Shortcut Pill
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.rivora_logo),
          contentDescription = "Rivora Logo",
          modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
        )
        Text(
          text = "Rivora",
          style = RivoraType.headingScreen.copy(color = RivoraColor.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
      }

      // Quick shortcut to Queue Page
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(RivoraColor.Surface)
          .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(999.dp))
          .clickable { onNavigateToQueue() }
          .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(RivoraColor.Orange)
        )
        Text(
          text = if (queueCount > 0) "Downloads ($queueCount) →" else "Downloads →",
          style = RivoraType.ButtonLabel.copy(
            color = RivoraColor.TextPrimary,
            fontWeight = FontWeight.SemiBold
          )
        )
      }
    }

    // 2. "New download" Card Centered on Screen
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentAlignment = Alignment.Center
    ) {
      RivoraCard(
        hasAura = true,
        modifier = Modifier.fillMaxWidth()
      ) {
        // Card Title matching Screenshot 1 & 2
        Text(
          text = "New download",
          style = RivoraType.headingCard.copy(
            color = RivoraColor.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
          )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Full Size Link Input Bar (fills the entire card width)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RivoraColor.SurfaceRaised)
            .border(1.dp, RivoraColor.BorderDefault, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          BasicTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            singleLine = true,
            cursorBrush = SolidColor(RivoraColor.Orange),
            textStyle = TextStyle(
              fontFamily = RivoraType.Mono,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Normal,
              color = RivoraColor.TextPrimary
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
              if (urlInput.isEmpty()) {
                Text(
                  text = "URL or magnet link...",
                  style = TextStyle(
                    fontFamily = RivoraType.Mono,
                    fontSize = 13.5.sp,
                    color = RivoraColor.TextMuted
                  )
                )
              }
              innerTextField()
            }
          )

          if (urlInput.isNotEmpty()) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clickable { urlInput = "" },
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "✕",
                style = TextStyle(
                  fontFamily = RivoraType.Mono,
                  fontSize = 13.sp,
                  color = Color(0xFF8C8C94)
                )
              )
            }
          }
        }

        // Feedback Area: Checking | Error | Warning | Verified File
        if (isChecking) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(RivoraColor.Orange)
            )
            Text(
              text = "Checking link...",
              style = RivoraType.rivoraText(RivoraType.Mono, 12f, FontWeight.Medium, 0f, 16f).copy(
                color = Color(0xFFA1A1A8)
              )
            )
          }
        } else if (probeError != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF221214))
              .border(1.dp, Color(0xFF6B2528), RoundedCornerShape(8.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5252))
            )
            Text(
              text = probeError!!,
              style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 16f).copy(
                color = Color(0xFFFF7070)
              )
            )
          }
        } else if (validationWarning != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF221A10))
              .border(1.dp, Color(0xFF7A4E1A), RoundedCornerShape(8.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFB74D))
            )
            Text(
              text = validationWarning!!,
              style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 16f).copy(
                color = Color(0xFFFFB74D)
              )
            )
          }
        } else if (detectedFile != null) {
          val file = detectedFile!!
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161619))
                .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(10.dp))
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              FileTile(extension = file.ext, color = Color(0xFFC0C0CC))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = file.name,
                  style = RivoraType.rivoraText(RivoraType.Sans, 13.5f, FontWeight.SemiBold, 0f, 18f).copy(
                    color = Color(0xFFEDEDEF)
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${file.size} · ${file.tag}",
                  style = RivoraType.rivoraText(RivoraType.Mono, 11.5f, FontWeight.Normal, 0f, 15f).copy(
                    color = Color(0xFFA1A1A8)
                  )
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(start = 4.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF4ADE80))
              )
              Text(
                text = "Downloadable file verified",
                style = RivoraType.rivoraText(RivoraType.Mono, 10.5f, FontWeight.Medium, 0f, 14f).copy(
                  color = Color(0xFF4ADE80)
                )
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save to Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Save to",
            style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.Normal, 0f, 18f).copy(
              color = Color(0xFFA1A1A8)
            )
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = savePath,
              style = RivoraType.rivoraText(RivoraType.Mono, 12.5f, FontWeight.Medium, 0f, 16f).copy(
                color = Color(0xFFEDEDEF)
              )
            )
            RivoraButton(
              text = "Change",
              onClick = { folderPickerLauncher.launch(null) },
              variant = RivoraButtonVariant.DARK,
              height = 30.dp
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Open .torrent Option Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Torrent file",
            style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.Normal, 0f, 18f).copy(
              color = Color(0xFFA1A1A8)
            )
          )
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF1B1B1F))
              .border(1.dp, Color(0xFF2E2E38), RoundedCornerShape(8.dp))
              .clickable { torrentPickerLauncher.launch("*/*") }
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            FolderOutlineIcon(modifier = Modifier.size(13.dp), color = Color(0xFFEDEDEF))
            Text(
              text = selectedTorrentName ?: "Open .torrent",
              style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 15f).copy(
                color = if (selectedTorrentName != null) RivoraColor.OrangeBright else Color(0xFFEDEDEF)
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Networks Row with Cellular & Wi-Fi pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Networks",
            style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.Normal, 0f, 18f).copy(
              color = Color(0xFFA1A1A8)
            )
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Cellular Pill
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF261C12))
                .border(1.dp, Color(0xFF8A4E1A), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                text = "Cellular",
                style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 16f).copy(
                  color = Color(0xFFEDEDEF)
                )
              )
            }

            // Wi-Fi Pill
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF102422))
                .border(1.dp, Color(0xFF236B64), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF4FD1C5))
              )
              Text(
                text = "Wi-Fi",
                style = RivoraType.rivoraText(RivoraType.Sans, 12f, FontWeight.Medium, 0f, 16f).copy(
                  color = Color(0xFFEDEDEF)
                )
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bottom Action Buttons matching Screenshot 1 & 2
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Cancel Button
          RivoraButton(
            text = "Cancel",
            onClick = {
              urlInput = ""
              onNavigateToQueue()
            },
            variant = RivoraButtonVariant.DARK,
            height = 36.dp
          )

          Spacer(modifier = Modifier.width(10.dp))

          // Action Button: "Checking" vs "Download"
          if (isChecking) {
            Box(
              modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF382C18))
                .border(1.dp, Color(0xFF6B481B), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Checking...",
                style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.SemiBold, 0f, 18f).copy(
                  color = Color(0xFFE5A044)
                )
              )
            }
          } else {
            val isDownloadReady = (detectedFile != null) && !isChecking && probeError == null && validationWarning == null
            Box(
              modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDownloadReady) Color(0xFFE5A044) else Color(0x1AFFFFFF))
                .border(1.dp, if (isDownloadReady) Color(0xFFE5A044) else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .clickable(enabled = isDownloadReady) {
                  val file = detectedFile ?: return@clickable
                  onStartDownload(urlInput.trim(), file.name, file.totalBytes, file.isResumable, selectedFolderUri, savePath)
                }
                .padding(horizontal = 18.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Download",
                style = RivoraType.rivoraText(RivoraType.Sans, 14f, FontWeight.SemiBold, 0f, 18f).copy(
                  color = if (isDownloadReady) Color(0xFF17171A) else Color(0x55FFFFFF)
                )
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
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
