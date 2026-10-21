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
    val contentType: String? = null,
    val isDownloadable: Boolean = true,
    val validationWarning: String? = null,
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

    private val DOWNLOADABLE_EXTENSIONS = setOf(
        // Video / Movies
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg",
        "ts", "m2ts", "3gp", "vob", "ogv", "rmvb", "asf", "f4v",
        // Audio / Music
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "alac", "aiff",
        "mid", "midi", "ape", "mka",
        // Archives / Compressed / Disk Images
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "img", "dmg",
        "tgz", "tbz2", "cab", "vhd", "vmdk", "z", "lz", "lzma", "cpio",
        // Executables / Installers / Software
        "exe", "msi", "apk", "xapk", "apkm", "deb", "rpm", "bin", "jar",
        "appimage", "pkg", "run", "ipa",
        // Documents / Books
        "pdf", "epub", "mobi", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "csv", "rtf", "odt", "ods", "odp", "azw3",
        // Torrents
        "torrent",
        // Data & Databases
        "sql", "db", "sqlite", "sqlite3"
    )

    private val WEB_PAGE_EXTENSIONS = setOf(
        "html", "htm", "php", "asp", "aspx", "jsp", "jspx", "cgi", "shtml", "xhtml"
    )

    /**
     * Probes a remote URL using a lightweight byte-range request.
     * Verifies whether the link represents a valid downloadable file (movie, video, audio, archive, executable, torrent, etc.)
     * or an HTML web page, checking HTTP headers, content-disposition, MIME types, and file extensions.
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
                contentType = "application/x-bittorrent",
                isDownloadable = true,
                isSuccess = true
            )
        }

        if (trimmedUrl.startsWith("content://", ignoreCase = true) || trimmedUrl.startsWith("file://", ignoreCase = true)) {
            val name = fallbackFileNameFromUrl(trimmedUrl)
            return@withContext ProbeResult(
                url = trimmedUrl,
                fileName = name,
                fileExtension = name.substringAfterLast('.', "TORRENT").uppercase(),
                totalBytes = 0L,
                isResumable = true,
                contentType = "application/x-bittorrent",
                isDownloadable = true,
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
                isDownloadable = false,
                isSuccess = false,
                errorMessage = "Invalid link protocol"
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

            // 2. Fallback attempt: Standard GET (if server rejects Range header with 400, 403, or 416)
            val fallbackRequest = Request.Builder()
                .url(trimmedUrl)
                .addHeader("User-Agent", "Rivora/1.0 (Android; Mobile)")
                .addHeader("Accept", "*/*")
                .build()

            client.newCall(fallbackRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext parseResponse(trimmedUrl, response, rangeSent = false)
                } else {
                    val errorMsg = formatHttpError(response.code, response.message)
                    return@withContext ProbeResult(
                        url = trimmedUrl,
                        fileName = fallbackFileNameFromUrl(trimmedUrl),
                        fileExtension = "FILE",
                        totalBytes = 0L,
                        isResumable = false,
                        isDownloadable = false,
                        isSuccess = false,
                        errorMessage = errorMsg
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
                isDownloadable = false,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Failed to connect to server"
            )
        }
    }

    private fun parseResponse(url: String, response: Response, rangeSent: Boolean): ProbeResult {
        val code = response.code
        val headers = response.headers
        val finalUrl = response.request.url.toString()

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

        val contentDisposition = headers["Content-Disposition"]
        val hasAttachment = contentDisposition?.contains("attachment", ignoreCase = true) == true
        val dispositionFileName = parseContentDisposition(contentDisposition)
        val urlPathFileName = extractUrlPathFileName(finalUrl)

        val rawContentType = headers["Content-Type"]
        val cleanContentType = rawContentType?.substringBefore(';')?.trim()?.lowercase() ?: ""

        // Resolve best file name
        val resolvedFileName = dispositionFileName
            ?: urlPathFileName
            ?: generateDefaultFileName(cleanContentType)

        val resolvedExt = resolvedFileName.substringAfterLast('.', "").lowercase()
        val extensionDisplay = resolvedExt.uppercase().let {
            if (it.isNotBlank() && it.length <= 6) it else inferExtension(cleanContentType)
        }

        val isHtmlContentType = cleanContentType == "text/html" ||
            cleanContentType == "application/xhtml+xml" ||
            cleanContentType == "text/vnd.wap.wml" ||
            cleanContentType == "application/vnd.wap.xhtml+xml"

        val isWebPageExt = WEB_PAGE_EXTENSIONS.contains(resolvedExt)
        val isDownloadableExt = DOWNLOADABLE_EXTENSIONS.contains(resolvedExt)
        val isDownloadableMime = isDownloadableMimeType(cleanContentType)

        val isDownloadable: Boolean
        val validationWarning: String?

        when {
            // 1. Explicit server attachment directive
            hasAttachment -> {
                isDownloadable = true
                validationWarning = null
            }
            // 2. Verified downloadable MIME type (video, audio, compressed, executable, APK, PDF, torrent, etc.)
            isDownloadableMime && !isHtmlContentType -> {
                isDownloadable = true
                validationWarning = null
            }
            // 3. Known downloadable file extension in URL and not serving HTML
            isDownloadableExt && !isHtmlContentType -> {
                isDownloadable = true
                validationWarning = null
            }
            // 4. HTML web page returned
            isHtmlContentType || isWebPageExt -> {
                isDownloadable = false
                validationWarning = "No file detected"
            }
            // 5. Web text or script files
            cleanContentType.startsWith("text/") || cleanContentType.contains("javascript") || cleanContentType.contains("css") -> {
                isDownloadable = false
                validationWarning = "No file detected"
            }
            // 6. Generic binary stream with positive size (e.g. cloudflare download generator or dynamic binary streams)
            totalBytes > 0L && (cleanContentType.isBlank() || cleanContentType == "application/octet-stream" || cleanContentType == "application/binary") -> {
                isDownloadable = true
                validationWarning = null
            }
            else -> {
                isDownloadable = false
                validationWarning = "No file detected"
            }
        }

        return ProbeResult(
            url = finalUrl,
            fileName = resolvedFileName,
            fileExtension = extensionDisplay,
            totalBytes = totalBytes,
            isResumable = isResumable,
            contentType = cleanContentType,
            isDownloadable = isDownloadable,
            validationWarning = validationWarning,
            etag = headers["ETag"],
            lastModified = headers["Last-Modified"],
            isSuccess = true
        )
    }

    private fun isDownloadableMimeType(cleanType: String): Boolean {
        if (cleanType.isBlank()) return false
        if (cleanType.startsWith("video/")) return true
        if (cleanType.startsWith("audio/")) return true
        if (cleanType.startsWith("image/")) return true
        if (cleanType.startsWith("font/")) return true
        if (cleanType == "application/octet-stream" ||
            cleanType == "application/x-binary" ||
            cleanType == "application/binary" ||
            cleanType == "binary/octet-stream" ||
            cleanType == "application/force-download"
        ) return true
        if (cleanType == "application/zip" ||
            cleanType.contains("rar") ||
            cleanType.contains("7z") ||
            cleanType.contains("tar") ||
            cleanType.contains("gzip") ||
            cleanType.contains("compressed") ||
            cleanType.contains("iso9660") ||
            cleanType == "application/x-apple-diskimage"
        ) return true
        if (cleanType == "application/vnd.android.package-archive" ||
            cleanType == "application/x-msdownload" ||
            cleanType == "application/x-msi" ||
            cleanType == "application/x-executable"
        ) return true
        if (cleanType == "application/pdf" ||
            cleanType == "application/epub+zip" ||
            cleanType.contains("msword") ||
            cleanType.contains("officedocument") ||
            cleanType.contains("opendocument")
        ) return true
        if (cleanType == "application/x-bittorrent") return true
        return false
    }

    private fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null

        // 1. RFC 5987 / RFC 6266 filename*=UTF-8''...
        val rfc5987Regex = """filename\*=UTF-8''([^;\s]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match5987 = rfc5987Regex.find(header)
        if (match5987 != null) {
            return try {
                URLDecoder.decode(match5987.groupValues[1], StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
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

    private fun extractUrlPathFileName(urlStr: String): String? {
        return try {
            val url = urlStr.toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments
            if (segments.isEmpty() || (segments.size == 1 && segments[0].isBlank())) return null
            val last = segments.lastOrNull { it.isNotBlank() } ?: return null
            val decoded = URLDecoder.decode(last, StandardCharsets.UTF_8.name()).trim()
            if (decoded.contains('.') && !decoded.endsWith('.')) {
                decoded
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackFileNameFromUrl(urlStr: String): String {
        return try {
            if (urlStr.startsWith("content://", ignoreCase = true) || urlStr.startsWith("file://", ignoreCase = true)) {
                val decoded = URLDecoder.decode(urlStr, StandardCharsets.UTF_8.name())
                val lastSegment = decoded.substringAfterLast('/').substringAfterLast(':').substringBefore('?')
                if (lastSegment.contains('.') && !lastSegment.endsWith('.')) {
                    return lastSegment
                }
            }
            val url = urlStr.toHttpUrlOrNull()
            val segment = url?.pathSegments?.lastOrNull { it.isNotBlank() }?.trim()
            if (!segment.isNullOrBlank() && segment.contains('.')) {
                URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
            } else {
                "download_${System.currentTimeMillis()}.bin"
            }
        } catch (_: Exception) {
            "download_${System.currentTimeMillis()}.bin"
        }
    }

    private fun generateDefaultFileName(contentType: String): String {
        val ext = inferExtension(contentType).lowercase()
        val suffix = if (ext == "file") "bin" else ext
        return "download_${System.currentTimeMillis()}.$suffix"
    }

    private fun inferExtension(contentType: String?): String {
        if (contentType.isNullOrBlank()) return "FILE"
        val cleanType = contentType.substringBefore(';').trim().lowercase()
        return when {
            cleanType == "video/x-matroska" -> "MKV"
            cleanType == "video/mp4" -> "MP4"
            cleanType == "video/quicktime" -> "MOV"
            cleanType == "video/x-msvideo" -> "AVI"
            cleanType == "video/webm" -> "WEBM"
            cleanType.startsWith("video/") -> cleanType.substringAfter("video/").uppercase()
            cleanType == "audio/mpeg" -> "MP3"
            cleanType == "audio/flac" -> "FLAC"
            cleanType == "audio/wav" || cleanType == "audio/x-wav" -> "WAV"
            cleanType == "audio/ogg" -> "OGG"
            cleanType == "audio/aac" -> "AAC"
            cleanType.startsWith("audio/") -> cleanType.substringAfter("audio/").uppercase()
            cleanType == "application/zip" -> "ZIP"
            cleanType.contains("rar") -> "RAR"
            cleanType.contains("7z") -> "7Z"
            cleanType.contains("tar") -> "TAR"
            cleanType.contains("gzip") -> "GZ"
            cleanType.contains("iso9660") -> "ISO"
            cleanType == "application/x-apple-diskimage" -> "DMG"
            cleanType == "application/pdf" -> "PDF"
            cleanType == "application/vnd.android.package-archive" -> "APK"
            cleanType == "application/x-msdownload" || cleanType == "application/x-executable" -> "EXE"
            cleanType == "application/x-msi" -> "MSI"
            cleanType == "application/x-bittorrent" -> "TORRENT"
            cleanType.startsWith("image/") -> cleanType.substringAfter("image/").uppercase()
            else -> "FILE"
        }
    }

    private fun formatHttpError(code: Int, message: String): String {
        return when (code) {
            404 -> "File not found (404)"
            403 -> "Access forbidden (403)"
            401 -> "Authorization required (401)"
            410 -> "File removed (410)"
            in 500..599 -> "Server error ($code)"
            else -> "Request failed ($code)"
        }
    }

    private fun extractMagnetDisplayName(magnetUri: String): String {
        val dnRegex = """[?&]dn=([^&]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = dnRegex.find(magnetUri)
        if (match != null) {
            return try {
                URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                match.groupValues[1]
            }
        }
        val xtRegex = """xt=urn:btih:([a-zA-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        val xtMatch = xtRegex.find(magnetUri)
        return xtMatch?.groupValues?.get(1)?.let { "torrent_$it" } ?: "torrent_download"
    }
}
