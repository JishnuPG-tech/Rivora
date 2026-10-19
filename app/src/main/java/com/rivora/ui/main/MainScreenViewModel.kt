package com.rivora.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivora.data.DownloadItem
import com.rivora.data.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

data class RivoraUiState(
  val currentView: String = "queue", // "queue" | "add" | "detail"
  val activeTab: Int = 0, // 0: Overview, 1: Throughput, 2: Chunks, 3: Networks
  val isPaused: Boolean = false,
  val isSlowMode: Boolean = false,
  val unit: String = "MB/s", // "MB/s" | "Mbps"
  val isCellularActive: Boolean = true,
  val activeModal: String? = null, // "newdl" | "networks" | "limits" | null
  val activeItem: DownloadItem? = null,
  val finishedItem: DownloadItem? = null,
  val wifiSpeedMBps: Float = 0f,
  val cellularSpeedMBps: Float = 0f,
  val wifiStreams: Int = 0,
  val cellularStreams: Int = 0,
  val wifiInterfaceName: String = "wlan0",
  val wifiIp: String? = null,
  val isWifiConnected: Boolean = false,
  val cellularInterfaceName: String = "rmnet0",
  val cellularIp: String? = null,
  val isCellularConnected: Boolean = false,
  val completedChunks: Int = 0,
  val totalChunks: Int = 200,
  val etaSeconds: Long = 0L,
  val queue: List<DownloadItem> = emptyList(),
  val finished: List<DownloadItem> = emptyList(),
  val throughputHistory: List<Float> = List(60) { 0f }
)

class MainScreenViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(RivoraUiState())
  val uiState: StateFlow<RivoraUiState> = _uiState.asStateFlow()

  init {
    observeNetworkManager()
    observeDownloadEngine()
  }

  private fun observeDownloadEngine() {
    viewModelScope.launch {
      try {
        com.rivora.backend.DownloadEngine.getInstance().engineState.collect { engineState ->
          _uiState.update { current ->
            current.copy(
              activeItem = engineState.activeItem,
              queue = engineState.queue,
              finished = engineState.finished,
              wifiSpeedMBps = engineState.wifiSpeedMBps,
              cellularSpeedMBps = engineState.cellularSpeedMBps,
              wifiStreams = engineState.wifiStreams,
              cellularStreams = engineState.cellularStreams,
              completedChunks = engineState.completedChunks,
              totalChunks = engineState.totalChunks,
              throughputHistory = engineState.throughputHistory,
              isPaused = engineState.isPaused,
              etaSeconds = engineState.etaSeconds
            )
          }
        }
      } catch (e: Exception) {
        // Ignored in preview or unit tests
      }
    }
  }

  private fun observeNetworkManager() {
    viewModelScope.launch {
      try {
        com.rivora.backend.MultiNetworkManager.getInstance().networkStates.collect { states ->
          val wifi = states[com.rivora.backend.NetworkAdapterType.WIFI]
          val cell = states[com.rivora.backend.NetworkAdapterType.CELLULAR]
          _uiState.update { current ->
            current.copy(
              wifiInterfaceName = wifi?.interfaceName ?: current.wifiInterfaceName,
              wifiIp = wifi?.ipAddress,
              isWifiConnected = wifi?.isConnected == true,
              cellularInterfaceName = cell?.interfaceName ?: current.cellularInterfaceName,
              cellularIp = cell?.ipAddress,
              isCellularConnected = cell?.isConnected == true
            )
          }
        }
      } catch (e: Exception) {
        // Ignored in preview or test
      }
    }
  }

  fun switchView(view: String) {
    _uiState.update { it.copy(currentView = view) }
  }

  fun startDownload(
    targetNameOrUrl: String,
    suggestedFileName: String? = null,
    totalBytes: Long? = null,
    isResumable: Boolean = true,
    customTreeUri: android.net.Uri? = null,
    displayPath: String = "Downloads/"
  ) {
    try {
      com.rivora.backend.DownloadEngine.getInstance().startDownload(
        url = targetNameOrUrl,
        suggestedFileName = suggestedFileName,
        totalBytes = totalBytes ?: 0L,
        isResumable = isResumable,
        customTreeUri = customTreeUri,
        displayPath = displayPath
      )
    } catch (e: Exception) {
      android.util.Log.e("MainScreenViewModel", "Failed to start download", e)
    }
    _uiState.update {
      it.copy(
        currentView = "detail",
        activeTab = 0
      )
    }
  }

  fun startQueuedDownload(itemId: String) {
    try {
      com.rivora.backend.DownloadEngine.getInstance().startQueuedDownload(itemId)
    } catch (_: Exception) {}
  }

  fun removeQueuedDownload(itemId: String) {
    try {
      com.rivora.backend.DownloadEngine.getInstance().removeQueuedDownload(itemId)
    } catch (_: Exception) {}
  }

  fun openDownloadedFile(item: com.rivora.data.DownloadItem) {
    try {
      com.rivora.backend.DownloadEngine.getInstance().openDownloadedFile(item)
    } catch (_: Exception) {}
  }

  fun selectTab(tab: Int) {
    _uiState.update { it.copy(activeTab = tab.coerceIn(0, 3)) }
  }

  fun togglePause() {
    try {
      com.rivora.backend.DownloadEngine.getInstance().togglePause()
    } catch (_: Exception) {}
    _uiState.update { it.copy(isPaused = !it.isPaused) }
  }

  fun cancelDownload() {
    try {
      com.rivora.backend.DownloadEngine.getInstance().cancelDownload()
    } catch (_: Exception) {}
    _uiState.update { it.copy(currentView = "queue", activeItem = null) }
  }

  fun deleteDownload(item: com.rivora.data.DownloadItem) {
    try {
      com.rivora.backend.DownloadEngine.getInstance().deleteDownload(item)
    } catch (_: Exception) {}
    _uiState.update { current ->
      current.copy(
        activeItem = if (current.activeItem?.id == item.id) null else current.activeItem,
        queue = current.queue.filterNot { it.id == item.id },
        finished = current.finished.filterNot { it.id == item.id }
      )
    }
  }

  fun toggleSlowMode() {
    _uiState.update { it.copy(isSlowMode = !it.isSlowMode) }
  }

  fun selectUnit(unit: String) {
    _uiState.update { it.copy(unit = unit) }
  }

  fun toggleCellular() {
    _uiState.update { current ->
      val newActive = !current.isCellularActive
      try {
        com.rivora.backend.DownloadEngine.getInstance().setCellularActive(newActive)
        if (newActive) {
          com.rivora.backend.MultiNetworkManager.getInstance().requestCellularConcurrency()
        } else {
          com.rivora.backend.MultiNetworkManager.getInstance().releaseCellularConcurrency()
        }
      } catch (e: Exception) {
        // Ignored in test/preview
      }
      current.copy(isCellularActive = newActive)
    }
  }

  fun openModal(modalName: String) {
    _uiState.update { it.copy(activeModal = modalName) }
  }

  fun closeModal() {
    _uiState.update { it.copy(activeModal = null) }
  }
}
