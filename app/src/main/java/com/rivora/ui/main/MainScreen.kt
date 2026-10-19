package com.rivora.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import com.rivora.ui.components.LiveStatusPill
import com.rivora.ui.screens.AddDownloadScreen
import com.rivora.ui.screens.ChunksTab
import com.rivora.ui.screens.DownloadsListScreen
import com.rivora.ui.screens.LimitsDialog
import com.rivora.ui.screens.NetworksDialog
import com.rivora.ui.screens.NetworksTab
import com.rivora.ui.screens.NewDownloadDialog
import com.rivora.ui.screens.OverviewTab
import com.rivora.ui.screens.QueueScreen
import com.rivora.ui.screens.ThroughputTab

/**
 * MainScreen orchestrating the Rivora UI layout.
 * Clean native mobile design with persistent bottom tab navigation bar,
 * uncluttered top telemetry bar, and full vertical content viewport.
 */
@Composable
fun MainScreen(
  viewModel: MainScreenViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val mult = if (state.unit == "Mbps") 8f else 1f
  val totalSpeed = if (state.isPaused) 0f else (state.wifiSpeedMBps + state.cellularSpeedMBps)
  val displayTotal = String.format("%.1f", totalSpeed * mult)
  val displayWifi = String.format("%.1f", state.wifiSpeedMBps * mult)
  val displayCell = String.format("%.1f", state.cellularSpeedMBps * mult)

  val avgSpeed = (state.throughputHistory.average().toFloat() * mult)
  val peakSpeed = ((state.throughputHistory.maxOrNull() ?: 2.4f) * mult)
  val displayAvg = String.format("%.1f", avgSpeed)
  val displayPeak = String.format("%.1f", peakSpeed)

  val downloadedGB = (state.activeItem?.downloadedBytes ?: 0L) / (1024f * 1024f * 1024f)
  val progressPercent = if ((state.activeItem?.totalBytes ?: 0L) > 0L) {
    (((state.activeItem?.downloadedBytes ?: 0L).toFloat() / (state.activeItem?.totalBytes ?: 1L)) * 100).toInt()
  } else 0

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(RivoraColor.App)
      .statusBarsPadding()
  ) {
    when (state.currentView) {
      "add" -> {
        AddDownloadScreen(
          queueCount = (if (state.activeItem != null) 1 else 0) + state.queue.size,
          cellularActive = state.isCellularActive,
          onNavigateToQueue = { viewModel.switchView("queue") },
          onStartDownload = { url, fileName, totalBytes, isResumable, customTreeUri, displayPath ->
            viewModel.startDownload(url, fileName, totalBytes, isResumable, customTreeUri, displayPath)
          },
          modifier = Modifier
            .weight(1f)
            .navigationBarsPadding()
        )
      }
      "queue", "list" -> {
        QueueScreen(
          activeItem = state.activeItem,
          queue = state.queue,
          finished = state.finished,
          currentSpeedText = displayTotal,
          unitText = state.unit,
          cellularActive = state.isCellularActive,
          isPaused = state.isPaused,
          onOpenDetail = { item ->
            viewModel.switchView("detail")
            viewModel.selectTab(0)
          },
          onNavigateToAdd = { viewModel.switchView("add") },
          onOpenNetworks = { viewModel.openModal("networks") },
          onTogglePause = { viewModel.togglePause() },
          onCancelActive = { viewModel.cancelDownload() },
          onStartQueued = { id -> viewModel.startQueuedDownload(id) },
          onRemoveQueued = { id -> viewModel.removeQueuedDownload(id) },
          onOpenFile = { item -> viewModel.openDownloadedFile(item) },
          onDeleteFinished = { item -> viewModel.deleteDownload(item) },
          isWifiConnected = state.isWifiConnected,
          isCellularConnected = state.isCellularConnected,
          modifier = Modifier
            .weight(1f)
            .navigationBarsPadding()
        )
      }
      else -> {
        // VIEW: ACTIVE DOWNLOAD DETAIL VIEW
        Column(modifier = Modifier.weight(1f)) {
          // 1. Seamless, Borderless Top Navigation Bar (Only ‹ Downloads)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier
                .clickable { viewModel.switchView("queue") }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "‹ Downloads",
                style = RivoraType.BackLabel.copy(
                  color = RivoraColor.TextPrimary,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 17.sp
                )
              )
            }
          }

        // 2. Scrollable Middle Viewport with Maximum Vertical Room
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          AnimatedContent(
            targetState = state.activeTab,
            transitionSpec = {
              (fadeIn(tween(180)) + slideInVertically(tween(180)) { 20 }) togetherWith fadeOut(tween(120))
            },
            label = "tabContent"
          ) { tabIdx ->
            when (tabIdx) {
              0 -> OverviewTab(
                fileName = state.activeItem?.fileName ?: "No active download",
                fileExt = state.activeItem?.fileExtension ?: "BIN",
                downloadedBytes = state.activeItem?.downloadedBytes ?: 0L,
                totalBytes = state.activeItem?.totalBytes ?: 0L,
                currentSpeedText = displayTotal,
                unitText = state.unit,
                avgSpeedText = displayAvg,
                peakSpeedText = displayPeak,
                wifiSpeedText = displayWifi,
                cellularSpeedText = displayCell,
                cellularActive = state.isCellularActive,
                completedChunks = state.completedChunks,
                totalChunks = state.totalChunks,
                wifiStreams = state.wifiStreams,
                isPaused = state.isPaused,
                etaText = if (state.etaSeconds > 0) "${state.etaSeconds}s left" else "Calculating...",
                history = state.throughputHistory.map { it * mult },
                onCancel = { viewModel.cancelDownload() },
                onTogglePause = { viewModel.togglePause() },
                onShowInFolder = { viewModel.switchView("queue") },
                onOpenFile = {
                  state.activeItem?.let { viewModel.openDownloadedFile(it) }
                },
                wifiInterface = state.wifiInterfaceName,
                cellularInterface = state.cellularInterfaceName,
                activeNetworksCount = (if (state.isWifiConnected) 1 else 0) + (if (state.isCellularActive && state.isCellularConnected) 1 else 0),
                isWifiConnected = state.isWifiConnected,
                isCellularConnected = state.isCellularConnected
              )
              1 -> ThroughputTab(
                currentSpeedText = displayTotal,
                unitText = state.unit,
                wifiSpeedText = displayWifi,
                cellularSpeedText = displayCell,
                cellularActive = state.isCellularActive,
                avgSpeedText = displayAvg,
                peakSpeedText = displayPeak,
                history = state.throughputHistory.map { it * mult },
                maxAxisValue = maxOf(peakSpeed * 1.15f, 10f),
                wifiStreams = state.wifiStreams,
                cellularStreams = state.cellularStreams,
                wifiInterface = state.wifiInterfaceName,
                cellularInterface = state.cellularInterfaceName,
                isWifiConnected = state.isWifiConnected,
                isCellularConnected = state.isCellularConnected
              )
              2 -> ChunksTab(
                completedChunks = state.completedChunks,
                cellularActive = state.isCellularActive,
                isPaused = state.isPaused,
                totalChunks = state.totalChunks,
                fileName = state.activeItem?.fileName ?: "No active download",
                fileExt = state.activeItem?.fileExtension ?: "BIN",
                downloadedBytes = state.activeItem?.downloadedBytes ?: 0L,
                totalBytes = state.activeItem?.totalBytes ?: 0L,
                currentSpeedMBps = state.wifiSpeedMBps + state.cellularSpeedMBps,
                etaText = if (state.etaSeconds > 0) "${state.etaSeconds}s left" else "Calculating..."
              )
              3 -> NetworksTab(
                currentSpeedText = displayTotal,
                wifiSpeedText = displayWifi,
                cellularSpeedText = displayCell,
                cellularActive = state.isCellularActive,
                unitText = state.unit,
                wifiStreams = state.wifiStreams,
                cellularStreams = state.cellularStreams,
                downloadedGB = downloadedGB,
                onToggleCellular = { viewModel.toggleCellular() },
                onOpenLimits = { viewModel.openModal("limits") },
                wifiInterface = state.wifiInterfaceName,
                cellularInterface = state.cellularInterfaceName,
                isCellularConnected = state.isCellularConnected,
                isWifiConnected = state.isWifiConnected
              )
            }
          }
        }

        // 3. Bottom Navigation Tab Bar (Fixed Permanently at Bottom)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(RivoraColor.Surface)
            .border(1.dp, RivoraColor.BorderSubtle)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(RivoraColor.TabTrack)
              .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(12.dp))
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            listOf("Overview", "Throughput", "Chunks", "Networks").forEachIndexed { idx, title ->
              val isSelected = state.activeTab == idx
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(38.dp)
                  .clip(RoundedCornerShape(9.dp))
                  .background(if (isSelected) RivoraColor.Elevated else Color.Transparent)
                  .then(if (isSelected) Modifier.border(1.dp, Color(0xFF383844), RoundedCornerShape(9.dp)) else Modifier)
                  .clickable { viewModel.selectTab(idx) },
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = title,
                  style = RivoraType.TabLabel.copy(
                    color = if (isSelected) RivoraColor.TextPrimary else RivoraColor.TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                  )
                )
              }
            }
          }
        }
      }
    }
  }
}

  // Modals & Dialog Overlays
  when (state.activeModal) {
    "newdl" -> NewDownloadDialog(
      onDismiss = { viewModel.closeModal() },
      onStartDownload = {
        viewModel.closeModal()
        viewModel.switchView("detail")
        viewModel.selectTab(0)
      },
      isWifiConnected = state.isWifiConnected,
      isCellularConnected = state.isCellularConnected
    )
    "networks" -> NetworksDialog(
      cellularActive = state.isCellularActive,
      onDismiss = { viewModel.closeModal() },
      onOpenLimits = {
        viewModel.closeModal()
        viewModel.openModal("limits")
      },
      isWifiConnected = state.isWifiConnected,
      isCellularConnected = state.isCellularConnected
    )
    "limits" -> LimitsDialog(
      onDismiss = { viewModel.closeModal() }
    )
  }
}
