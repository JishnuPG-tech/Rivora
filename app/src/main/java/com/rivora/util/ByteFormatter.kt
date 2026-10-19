package com.rivora.util

import java.util.Locale

/**
 * Universal file size and download progress formatter.
 * Automatically chooses the appropriate scale (B, KB, MB, GB, TB)
 * without hardcoding everything to GB.
 */
object ByteFormatter {

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
     * Formats downloaded of total matching the unit of the total file size.
     * Examples:
     * - 200MB file: "120.3 MB of 231.5 MB"
     * - 2.4GB file: "1.25 GB of 2.40 GB"
     * - 800KB file: "410.2 KB of 800.0 KB"
     * - Unknown total: "120.3 MB"
     */
    fun formatProgress(downloaded: Long, total: Long): String {
        if (total <= 0L) return formatBytes(downloaded)

        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0

        val tot = total.toDouble()
        val down = downloaded.toDouble()

        return when {
            tot >= tb -> String.format(Locale.US, "%.2f TB of %.2f TB", down / tb, tot / tb)
            tot >= gb -> String.format(Locale.US, "%.2f GB of %.2f GB", down / gb, tot / gb)
            tot >= mb -> String.format(Locale.US, "%.1f MB of %.1f MB", down / mb, tot / mb)
            tot >= kb -> String.format(Locale.US, "%.1f KB of %.1f KB", down / kb, tot / kb)
            else -> "$downloaded B of $total B"
        }
    }
}
