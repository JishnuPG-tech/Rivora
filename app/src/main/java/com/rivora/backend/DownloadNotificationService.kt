package com.rivora.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rivora.MainActivity
import com.rivora.R
import com.rivora.data.DownloadStatus
import com.rivora.util.ByteFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * DownloadNotificationService:
 * Foreground Service running high-priority status bar notifications for downloads.
 * Displays real-time progress, speed, ETA, and Pause/Resume/Cancel quick actions.
 */
class DownloadNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "rivora_downloads_channel"
        const val CHANNEL_NAME = "Rivora Downloads"
        const val NOTIFICATION_ID = 1001
        const val COMPLETE_NOTIFICATION_ID = 1002

        const val ACTION_START = "com.rivora.START_NOTIFICATION"
        const val ACTION_STOP = "com.rivora.STOP_NOTIFICATION"
        const val ACTION_TOGGLE_PAUSE = "com.rivora.TOGGLE_PAUSE"
        const val ACTION_CANCEL_DOWNLOAD = "com.rivora.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, DownloadNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                try {
                    DownloadEngine.getInstance(this).togglePause()
                } catch (_: Exception) {}
            }
            ACTION_CANCEL_DOWNLOAD -> {
                try {
                    DownloadEngine.getInstance(this).cancelDownload()
                } catch (_: Exception) {}
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundWithInitialNotification()
                observeEngine()
            }
            else -> {
                startForegroundWithInitialNotification()
                observeEngine()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress for downloads"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithInitialNotification() {
        val notification = buildDownloadNotification(
            title = "Rivora Download",
            content = "Preparing high-speed download...",
            progress = 0,
            isIndeterminate = true,
            isPaused = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeEngine() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            val engine = DownloadEngine.getInstance(this@DownloadNotificationService)
            engine.engineState.collect { state ->
                val active = state.activeItem
                if (active != null && active.status == DownloadStatus.ACTIVE) {
                    val progress = if (active.totalBytes > 0) {
                        ((active.downloadedBytes.toFloat() / active.totalBytes) * 100).toInt().coerceIn(0, 100)
                    } else 0

                    val totalSpeed = state.wifiSpeedMBps + state.cellularSpeedMBps
                    val speedStr = if (totalSpeed <= 0f) "0.0 KB/s" else ByteFormatter.formatSpeedText(totalSpeed, "MB/s")
                    val sizeText = ByteFormatter.formatProgress(active.downloadedBytes, active.totalBytes)
                    val etaText = if (state.etaSeconds > 0) " · ${ByteFormatter.formatEta(state.etaSeconds)}" else ""
                    val contentText = if (state.isPaused) "Paused · $sizeText" else "$speedStr · $sizeText$etaText"

                    val notification = buildDownloadNotification(
                        title = active.fileName,
                        content = contentText,
                        progress = progress,
                        isIndeterminate = active.totalBytes <= 0,
                        isPaused = state.isPaused
                    )
                    notificationManager.notify(NOTIFICATION_ID, notification)

                } else if (!state.isDownloading && state.queue.isEmpty()) {
                    // Check if there was a finished download to display
                    val latestFinished = state.finished.firstOrNull()
                    if (latestFinished != null && latestFinished.status == DownloadStatus.FINISHED) {
                        showCompletedNotification(latestFinished.fileName)
                    }
                    stopForegroundService()
                }
            }
        }
    }

    private fun buildDownloadNotification(
        title: String,
        content: String,
        progress: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean
    ): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume Action
        val pauseIntent = Intent(this, DownloadNotificationService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel Action
        val cancelIntent = Intent(this, DownloadNotificationService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.rivora_logo))
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                if (isPaused) "Resume" else "Pause",
                pausePendingIntent
            )
            .addAction(
                0,
                "Cancel",
                cancelPendingIntent
            )

        if (isIndeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    private fun showCompletedNotification(fileName: String) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.rivora_logo))
            .setContentTitle("Download Complete")
            .setContentText(fileName)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, completeNotification)
    }

    private fun stopForegroundService() {
        observerJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
