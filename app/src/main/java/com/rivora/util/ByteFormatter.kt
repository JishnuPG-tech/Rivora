package com.rivora.util

import java.util.Locale

/**
 * Universal file size, download progress, transfer speed, and duration formatter.
 * Automatically chooses the appropriate scale (B, KB, MB, GB, TB)
 * and formats durations into human-readable hours, minutes, and seconds.
 */
object ByteFormatter {

    data class FormattedSpeed(
        val value: String,
        val unit: String
    ) {
        val text: String get() = "$value $unit"
    }

    /**
     * Formats raw bytes into appropriately scaled file sizes (B, KB, MB, GB, TB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val b = bytes.toDouble()
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0

        return when {
            b >= tb -> String.format(Locale.US, "%.2f TB", b / tb)
            b >= gb -> String.format(Locale.US, "%.2f GB", b / gb)
            b >= mb -> String.format(Locale.US, "%.1f MB", b / mb)
            b >= kb -> String.format(Locale.US, "%.1f KB", b / kb)
            else -> "$bytes B"
        }
    }

    /**
     * Formats downloaded bytes dynamically (B -> KB -> MB -> GB) against the total file size.
     * Examples:
     * - Starting 1.78 GB download: "450.0 KB of 1.78 GB" -> "12.5 MB of 1.78 GB" -> "1.25 GB of 1.78 GB"
     * - Unknown total: "120.3 MB"
     */
    fun formatProgress(downloaded: Long, total: Long): String {
        if (total <= 0L) return formatBytes(downloaded)
        return "${formatBytes(downloaded)} of ${formatBytes(total)}"
    }

    /**
     * Dynamically formats transfer speed based on value and unit mode.
     * In Bytes mode ("MB/s"), scales dynamically across B/s, KB/s, MB/s, GB/s, TB/s.
     * In Bits mode ("Mbps"), scales dynamically across bps, Kbps, Mbps, Gbps.
     */
    fun formatSpeed(speedMBps: Float, unitMode: String = "MB/s"): FormattedSpeed {
        val isBits = unitMode.equals("Mbps", ignoreCase = true)
        val bytesPerSec = (speedMBps.toDouble() * 1024.0 * 1024.0).coerceAtLeast(0.0)

        if (isBits) {
            val bitsPerSec = bytesPerSec * 8.0
            val kb = 1024.0
            val mbThreshold = 1000.0 * kb
            val gbThreshold = 1000.0 * 1024.0 * kb
            val mb = kb * 1024.0
            val gb = mb * 1024.0

            return when {
                bitsPerSec <= 0.0 -> FormattedSpeed("0.0", "Kbps")
                bitsPerSec < kb -> FormattedSpeed(String.format(Locale.US, "%.0f", bitsPerSec), "bps")
                bitsPerSec < mbThreshold -> FormattedSpeed(String.format(Locale.US, "%.1f", bitsPerSec / kb), "Kbps")
                bitsPerSec < gbThreshold -> FormattedSpeed(String.format(Locale.US, "%.1f", bitsPerSec / mb), "Mbps")
                else -> FormattedSpeed(String.format(Locale.US, "%.2f", bitsPerSec / gb), "Gbps")
            }
        } else {
            val kb = 1024.0
            val mbThreshold = 1000.0 * kb
            val gbThreshold = 1000.0 * 1024.0 * kb
            val mb = 1024.0 * 1024.0
            val gb = mb * 1024.0
            val tb = gb * 1024.0

            return when {
                bytesPerSec <= 0.0 -> FormattedSpeed("0.0", "KB/s")
                bytesPerSec < kb -> FormattedSpeed(String.format(Locale.US, "%.0f", bytesPerSec), "B/s")
                bytesPerSec < mbThreshold -> FormattedSpeed(String.format(Locale.US, "%.1f", bytesPerSec / kb), "KB/s")
                bytesPerSec < gbThreshold -> FormattedSpeed(String.format(Locale.US, "%.1f", bytesPerSec / mb), "MB/s")
                bytesPerSec < tb -> FormattedSpeed(String.format(Locale.US, "%.2f", bytesPerSec / gb), "GB/s")
                else -> FormattedSpeed(String.format(Locale.US, "%.2f", bytesPerSec / tb), "TB/s")
            }
        }
    }

    /**
     * Convenience method returning formatted value with unit appended (e.g. "450.2 KB/s", "12.4 MB/s").
     */
    fun formatSpeedText(speedMBps: Float, unitMode: String = "MB/s"): String {
        return formatSpeed(speedMBps, unitMode).text
    }

    /**
     * Formats estimated remaining seconds into human-readable time with proper units.
     * Examples:
     * - 25 -> "25s left"
     * - 134 -> "2m 14s left"
     * - 3600 -> "1h left"
     * - 3690 -> "1h 1m left"
     * - 90000 -> "1d 1h left"
     * - 0 or negative -> "Calculating..."
     */
    fun formatEta(seconds: Long, includeSuffix: Boolean = true): String {
        if (seconds <= 0L) return "Calculating..."

        val suffix = if (includeSuffix) " left" else ""
        return when {
            seconds < 60L -> "${seconds}s$suffix"
            seconds < 3600L -> {
                val mins = seconds / 60L
                val secs = seconds % 60L
                if (secs > 0L) "${mins}m ${secs}s$suffix" else "${mins}m$suffix"
            }
            seconds < 86400L -> {
                val hours = seconds / 3600L
                val mins = (seconds % 3600L) / 60L
                if (mins > 0L) "${hours}h ${mins}m$suffix" else "${hours}h$suffix"
            }
            else -> {
                val days = seconds / 86400L
                val hours = (seconds % 86400L) / 3600L
                if (hours > 0L) "${days}d ${hours}h$suffix" else "${days}d$suffix"
            }
        }
    }
}
