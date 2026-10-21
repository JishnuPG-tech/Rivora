package com.rivora.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", ByteFormatter.formatBytes(0L))
        assertEquals("500 B", ByteFormatter.formatBytes(500L))
        assertEquals("1.5 KB", ByteFormatter.formatBytes(1536L))
        assertEquals("10.0 MB", ByteFormatter.formatBytes(10L * 1024 * 1024))
        assertEquals("2.50 GB", ByteFormatter.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testFormatSpeedBytesMode() {
        // Zero
        val zero = ByteFormatter.formatSpeed(0f, "MB/s")
        assertEquals("0.0", zero.value)
        assertEquals("KB/s", zero.unit)
        assertEquals("0.0 KB/s", zero.text)

        // Low speed: 50 KB/s
        val lowMBps = 50f / 1024f
        val low = ByteFormatter.formatSpeed(lowMBps, "MB/s")
        assertEquals("50.0", low.value)
        assertEquals("KB/s", low.unit)
        assertEquals("50.0 KB/s", low.text)

        // Moderate speed: 12.4 MB/s
        val mod = ByteFormatter.formatSpeed(12.4f, "MB/s")
        assertEquals("12.4", mod.value)
        assertEquals("MB/s", mod.unit)
        assertEquals("12.4 MB/s", mod.text)

        // High speed: 1.5 GB/s = 1536 MB/s
        val high = ByteFormatter.formatSpeed(1536f, "MB/s")
        assertEquals("1.50", high.value)
        assertEquals("GB/s", high.unit)
        assertEquals("1.50 GB/s", high.text)
    }

    @Test
    fun testFormatSpeedBitsMode() {
        // Zero
        val zero = ByteFormatter.formatSpeed(0f, "Mbps")
        assertEquals("0.0", zero.value)
        assertEquals("Kbps", zero.unit)

        // 100 Mbps = 12.5 MB/s
        val hundredMbps = ByteFormatter.formatSpeed(12.5f, "Mbps")
        assertEquals("100.0", hundredMbps.value)
        assertEquals("Mbps", hundredMbps.unit)
        assertEquals("100.0 Mbps", hundredMbps.text)
    }

    @Test
    fun testFormatEta() {
        assertEquals("Calculating...", ByteFormatter.formatEta(0L))
        assertEquals("Calculating...", ByteFormatter.formatEta(-10L))

        // Seconds
        assertEquals("45s left", ByteFormatter.formatEta(45L))
        assertEquals("45s", ByteFormatter.formatEta(45L, includeSuffix = false))

        // Minutes and seconds
        assertEquals("1m left", ByteFormatter.formatEta(60L))
        assertEquals("2m 5s left", ByteFormatter.formatEta(125L))
        assertEquals("15m 30s left", ByteFormatter.formatEta(930L))

        // Hours and minutes
        assertEquals("1h left", ByteFormatter.formatEta(3600L))
        assertEquals("1h 1m left", ByteFormatter.formatEta(3665L))
        assertEquals("2h 15m left", ByteFormatter.formatEta(8100L))

        // Days and hours
        assertEquals("1d left", ByteFormatter.formatEta(86400L))
        assertEquals("1d 1h left", ByteFormatter.formatEta(90000L))
    }
}
