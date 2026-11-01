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
    
    val origHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      android.util.Log.e("RivoraCrash", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
      try {
        val crashFile = java.io.File(getExternalFilesDir(null) ?: filesDir, "crash.log")
        crashFile.writeText("Crash on ${thread.name}: ${throwable.stackTraceToString()}\n")
      } catch (_: Throwable) {}
      origHandler?.uncaughtException(thread, throwable)
    }

    MultiNetworkManager.getInstance(this).startMonitoring()
    DownloadEngine.getInstance(this)
    com.rivora.updater.AppUpdateManager.getInstance(this).checkForUpdatesOnStartup()

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
}
