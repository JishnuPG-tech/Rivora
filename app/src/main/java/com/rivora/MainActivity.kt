package com.rivora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rivora.backend.DownloadEngine
import com.rivora.backend.MultiNetworkManager
import com.rivora.theme.RivoraTheme

class MainActivity : ComponentActivity() {

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ -> }

  fun checkAndRequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    MultiNetworkManager.getInstance(this).startMonitoring()
    DownloadEngine.getInstance(this)

    enableEdgeToEdge()
    setContent {
      RivoraTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainNavigation(
            onRequestNotificationPermission = { checkAndRequestNotificationPermission() }
          )
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    MultiNetworkManager.getInstance(this).stopMonitoring()
  }
}
