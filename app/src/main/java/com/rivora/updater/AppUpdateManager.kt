package com.rivora.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AppUpdateManager private constructor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_REPO = "JishnuPG-tech/Rivora"
        private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val MIN_BACKGROUND_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours

        @Volatile
        private var INSTANCE: AppUpdateManager? = null

        fun getInstance(context: Context? = null): AppUpdateManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context?.applicationContext 
                    ?: throw IllegalStateException("AppUpdateManager must be initialized with a Context")
                INSTANCE ?: AppUpdateManager(appCtx).also { INSTANCE = it }
            }
        }

        fun isNewerVersion(remoteVer: String, currentVer: String): Boolean {
            try {
                val rParts = remoteVer.removePrefix("v").removePrefix("V").split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
                val cParts = currentVer.removePrefix("v").removePrefix("V").split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

                val maxLen = maxOf(rParts.size, cParts.size)
                for (i in 0 until maxLen) {
                    val r = rParts.getOrElse(i) { 0 }
                    val c = cParts.getOrElse(i) { 0 }
                    if (r > c) return true
                    if (r < c) return false
                }
                return false
            } catch (_: Exception) {
                return remoteVer != currentVer
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val prefs = context.getSharedPreferences("rivora_updater_prefs", Context.MODE_PRIVATE)

    private val _checkResult = MutableStateFlow<UpdateCheckResult?>(null)
    val checkResult: StateFlow<UpdateCheckResult?> = _checkResult.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.1.3"
        } catch (_: Exception) {
            "1.1.3"
        }
    }

    fun checkForUpdatesOnStartup() {
        val lastCheck = prefs.getLong("last_check_timestamp", 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck < MIN_BACKGROUND_INTERVAL_MS) {
            Log.d(TAG, "Skipping background update check; last check was ${(now - lastCheck) / 60000} minutes ago")
            return
        }
        checkForUpdates(isManual = false)
    }

    fun checkForUpdates(isManual: Boolean = true) {
        scope.launch {
            try {
                prefs.edit().putLong("last_check_timestamp", System.currentTimeMillis()).apply()
                val currentVer = getCurrentVersionName()

                val requestBuilder = Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Rivora-Android-App/$currentVer")

                val lastEtag = prefs.getString("last_etag", null)
                if (!isManual && !lastEtag.isNullOrBlank()) {
                    requestBuilder.header("If-None-Match", lastEtag)
                }

                val response = client.newCall(requestBuilder.build()).execute()
                response.use { res ->
                    if (res.code == 304) {
                        Log.d(TAG, "GitHub releases unchanged (304 Not Modified)")
                        _checkResult.value = UpdateCheckResult.UpToDate(currentVer)
                        return@launch
                    }

                    if (!res.isSuccessful) {
                        val msg = "GitHub API returned ${res.code}"
                        Log.w(TAG, msg)
                        if (isManual) _checkResult.value = UpdateCheckResult.Error(msg)
                        return@launch
                    }

                    res.header("ETag")?.let { etag ->
                        prefs.edit().putString("last_etag", etag).apply()
                    }

                    val bodyStr = res.body?.string() ?: throw Exception("Empty release body")
                    val json = JSONObject(bodyStr)
                    val tagName = json.getString("tag_name")
                    val releaseName = json.optString("name", tagName)
                    val bodyNotes = json.optString("body", "No release notes provided.")
                    val publishedAt = json.optString("published_at", "")
                    val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO/releases")

                    var apkAsset: ReleaseAsset? = null
                    val assetsArr = json.optJSONArray("assets")
                    if (assetsArr != null) {
                        for (i in 0 until assetsArr.length()) {
                            val assetJson = assetsArr.getJSONObject(i)
                            val aName = assetJson.getString("name")
                            if (aName.endsWith(".apk", ignoreCase = true)) {
                                apkAsset = ReleaseAsset(
                                    name = aName,
                                    downloadUrl = assetJson.getString("browser_download_url"),
                                    sizeBytes = assetJson.optLong("size", 0L),
                                    contentType = assetJson.optString("content_type", "application/vnd.android.package-archive")
                                )
                                if (!aName.contains("debug", ignoreCase = true)) {
                                    break
                                }
                            }
                        }
                    }

                    val parsedRelease = GitHubRelease(
                        tagName = tagName,
                        name = releaseName,
                        body = bodyNotes,
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl,
                        apkAsset = apkAsset
                    )

                    val cleanNewVer = tagName.removePrefix("v").removePrefix("V").trim()
                    val cleanCurrentVer = currentVer.removePrefix("v").removePrefix("V").trim()

                    if (isNewerVersion(cleanNewVer, cleanCurrentVer)) {
                        Log.d(TAG, "Update available: $currentVer -> $tagName")
                        _checkResult.value = UpdateCheckResult.UpdateAvailable(
                            currentVersion = currentVer,
                            newVersion = tagName,
                            release = parsedRelease
                        )
                    } else {
                        Log.d(TAG, "App is up to date: $currentVer (latest: $tagName)")
                        _checkResult.value = UpdateCheckResult.UpToDate(currentVer)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
                if (isManual) {
                    _checkResult.value = UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
                }
            }
        }
    }

    fun startDownloadUpdate(release: GitHubRelease) {
        val asset = release.apkAsset ?: return
        scope.launch {
            try {
                _downloadState.value = UpdateDownloadState.Downloading(0, 0L, asset.sizeBytes)

                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(updateDir, asset.name)
                if (targetFile.exists()) targetFile.delete()

                val request = Request.Builder()
                    .url(asset.downloadUrl)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "Rivora-Android-Updater")
                    .build()

                val response = client.newCall(request).execute()
                response.use { res ->
                    if (!res.isSuccessful) throw Exception("Download failed with HTTP ${res.code}")
                    val body = res.body ?: throw Exception("Empty response body")
                    val total = if (asset.sizeBytes > 0L) asset.sizeBytes else body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(65536)
                            var readBytes: Int
                            var downloaded = 0L
                            var lastPercent = -1

                            while (input.read(buffer).also { readBytes = it } != -1) {
                                output.write(buffer, 0, readBytes)
                                downloaded += readBytes
                                val percent = if (total > 0L) ((downloaded * 100) / total).toInt() else 0
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _downloadState.value = UpdateDownloadState.Downloading(percent, downloaded, total)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile, release)
                installApk(targetFile)

            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading update APK: ${e.message}", e)
                _downloadState.value = UpdateDownloadState.Failed(e.message ?: "Download failed")
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.w(TAG, "Cannot install APK: file does not exist: ${apkFile.absolutePath}")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Failed("Install failed: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _checkResult.value = null
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun isNewerVersion(remoteVer: String, currentVer: String): Boolean {
        try {
            val rParts = remoteVer.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val cParts = currentVer.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (_: Exception) {
            return remoteVer != currentVer
        }
    }
}
