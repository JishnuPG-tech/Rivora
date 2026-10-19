package com.rivora.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ProbeResult(
    val url: String,
    val fileName: String,
    val fileExtension: String,
    val totalBytes: Long,
    val isResumable: Boolean,
    val etag: String? = null,
    val lastModified: String? = null,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

object UrlProber {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val CONTENT_RANGE_PATTERN = Pattern.compile("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", Pattern.CASE_INSENSITIVE)

    /**
     * Probes a remote URL using a lightweight 1KB byte-range request.
     * Verifies range support, file size, content-disposition, and version tags.
     */
    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()

        if (trimmedUrl.startsWith("magnet:", ignoreCase = true)) {
            val name = extractMagnetDisplayName(trimmedUrl)
            return@withContext ProbeResult(
                url = trimmedUrl,
                fileName = name,
                fileExtension = name.substringAfterLast('.', "TORRENT").uppercase(),
                totalBytes = 0L,
                isResumable = true,
                isSuccess = true
            )
        }

        if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
            !trimmedUrl.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext ProbeResult(
                url = trimmedUrl,
                fileName = "Unknown",
                fileExtension = "FILE",
                totalBytes = 0L,
                isResumable = false,
                isSuccess = false,
                errorMessage = "Invalid URL protocol (must be http or https)"
            )
        }

        try {
            // 1. First attempt: GET with Range: bytes=0-1023
            val rangeRequest = Request.Builder()
                .url(trimmedUrl)
                .addHeader("Range", "bytes=0-1023")
                .addHeader("User-Agent", "Rivora/1.0 (Android; Mobile)")
                .addHeader("Accept", "*/*")
                .build()

            client.newCall(rangeRequest).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    return@withContext parseResponse(trimmedUrl, response, rangeSent = true)
                }
            }

            // 2. Fallback attempt: Standard GET (if server rejects Range header with 400 or 403)
            val fallbackRequest = Request.Builder()
                .url(trimmedUrl)
                .addHeader("User-Agent", "Rivora/1.0 (Android; Mobile)")
                .addHeader("Accept", "*/*")
                .build()

            client.newCall(fallbackRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext parseResponse(trimmedUrl, response, rangeSent = false)
                } else {
                    return@withContext ProbeResult(
                        url = trimmedUrl,
                        fileName = fallbackFileNameFromUrl(trimmedUrl),
                        fileExtension = "FILE",
                        totalBytes = 0L,
                        isResumable = false,
                        isSuccess = false,
                        errorMessage = "HTTP error ${response.code}: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            ProbeResult(
                url = trimmedUrl,
                fileName = fallbackFileNameFromUrl(trimmedUrl),
                fileExtension = "FILE",
                totalBytes = 0L,
                isResumable = false,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Failed to connect to server"
            )
        }
    }

    private fun parseResponse(url: String, response: Response, rangeSent: Boolean): ProbeResult {
        val code = response.code
        val headers = response.headers

        val isResumable = (code == 206) || headers["Accept-Ranges"]?.contains("bytes", ignoreCase = true) == true
        var totalBytes = -1L

        val contentRange = headers["Content-Range"]
        if (contentRange != null) {
            val matcher = CONTENT_RANGE_PATTERN.matcher(contentRange)
            if (matcher.find()) {
                val totalStr = matcher.group(3)
                if (totalStr != null && totalStr != "*") {
                    totalBytes = totalStr.toLongOrNull() ?: -1L
                }
            }
        }

        if (totalBytes <= 0L) {
            val cl = headers["Content-Length"]?.toLongOrNull() ?: -1L
            if (code == 200 && cl > 0L) {
                totalBytes = cl
            }
        }

        val resolvedFileName = parseContentDisposition(headers["Content-Disposition"])
            ?: fallbackFileNameFromUrl(response.request.url.toString())

        val extension = resolvedFileName.substringAfterLast('.', "").uppercase().let { ext ->
            if (ext.isNotBlank() && ext.length <= 5) ext else inferExtension(headers["Content-Type"])
        }

        return ProbeResult(
            url = response.request.url.toString(),
            fileName = resolvedFileName,
            fileExtension = extension,
            totalBytes = totalBytes,
            isResumable = isResumable,
            etag = headers["ETag"],
            lastModified = headers["Last-Modified"],
            isSuccess = true
        )
    }

    private fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null

        // 1. RFC 5987 / RFC 6266 filename*=UTF-8''...
        val rfc5987Regex = """filename\*=UTF-8''([^;\s]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match5987 = rfc5987Regex.find(header)
        if (match5987 != null) {
            return try {
                URLDecoder.decode(match5987.groupValues[1], StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                null
            }
        }

        // 2. Standard filename="..."
        val standardRegex = """filename="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
        val matchStd = standardRegex.find(header)
        if (matchStd != null) {
            return matchStd.groupValues[1]
        }

        // 3. Unquoted filename=...
        val unquotedRegex = """filename=([^;\s]+)""".toRegex(RegexOption.IGNORE_CASE)
        val matchUnquoted = unquotedRegex.find(header)
        if (matchUnquoted != null) {
            return matchUnquoted.groupValues[1]
        }

        return null
    }

    private fun fallbackFileNameFromUrl(urlStr: String): String {
        return try {
            val url = urlStr.toHttpUrlOrNull()
            val segment = url?.pathSegments?.lastOrNull()?.trim()
            if (!segment.isNullOrBlank()) {
                URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
            } else {
                "download.bin"
            }
        } catch (e: Exception) {
            "download.bin"
        }
    }

    private fun inferExtension(contentType: String?): String {
        if (contentType.isNullOrBlank()) return "FILE"
        val cleanType = contentType.substringBefore(';').trim().lowercase()
        return when (cleanType) {
            "video/x-matroska" -> "MKV"
            "video/mp4" -> "MP4"
            "video/quicktime" -> "MOV"
            "video/x-msvideo" -> "AVI"
            "application/zip" -> "ZIP"
            "application/x-rar-compressed", "application/vnd.rar" -> "RAR"
            "application/x-7z-compressed" -> "7Z"
            "application/x-iso9660-image" -> "ISO"
            "application/pdf" -> "PDF"
            "application/vnd.android.package-archive" -> "APK"
            "audio/mpeg" -> "MP3"
            "audio/flac" -> "FLAC"
            else -> "FILE"
        }
    }

    private fun extractMagnetDisplayName(magnetUri: String): String {
        val dnRegex = """[?&]dn=([^&]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = dnRegex.find(magnetUri)
        if (match != null) {
            return try {
                URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                match.groupValues[1]
            }
        }
        val xtRegex = """xt=urn:btih:([a-zA-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        val xtMatch = xtRegex.find(magnetUri)
        return xtMatch?.groupValues?.get(1)?.let { "torrent_$it" } ?: "torrent_download"
    }
}
