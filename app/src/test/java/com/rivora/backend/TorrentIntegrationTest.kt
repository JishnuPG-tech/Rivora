package com.rivora.backend

import com.rivora.backend.torrent.TorrentInfoModel
import com.rivora.backend.torrent.TorrentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentIntegrationTest {

    @Test
    fun testIsTorrentUrlDetection() {
        // Magnet link with various query parameters
        assertTrue(UrlProber.isTorrentUrl("magnet:?xt=urn:btih:da39a3ee5e6b4b0d3255bfef95601890afd80709&dn=Ubuntu+24.04"))
        assertTrue(UrlProber.isTorrentUrl("MAGNET:?XT=URN:BTIH:12345"))

        // .torrent file URLs and schemes
        assertTrue(UrlProber.isTorrentUrl("https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso.torrent"))
        assertTrue(UrlProber.isTorrentUrl("http://example.com/file.torrent"))
        assertTrue(UrlProber.isTorrentUrl("content://com.android.providers.downloads.documents/document/123.torrent"))
        assertTrue(UrlProber.isTorrentUrl("file:///sdcard/Download/debian.torrent"))

        // Standard HTTP direct file links (not torrents)
        assertFalse(UrlProber.isTorrentUrl("https://example.com/video.mp4"))
        assertFalse(UrlProber.isTorrentUrl("https://example.com/archive.zip"))
        assertFalse(UrlProber.isTorrentUrl("http://example.com/setup.exe"))
    }

    @Test
    fun testTorrentPieceToChunkMapping() {
        val totalChunks = 48
        val piecesCount = 960 // e.g. 960 pieces of 1MB = 960MB torrent
        val piecesCompleted = 480 // 50% completed

        val progress = piecesCompleted.toFloat() / piecesCount
        val completedChunksCount = ((progress * totalChunks).toInt()).coerceIn(0, totalChunks)

        // Half of the pieces completed should map to exactly half of the 48 matrix chunks
        assertEquals(24, completedChunksCount)

        // 100% completed maps to 48
        val fullCompleted = ((1.0f * totalChunks).toInt()).coerceIn(0, totalChunks)
        assertEquals(48, fullCompleted)
    }

    @Test
    fun testTorrentInfoModelDefaults() {
        val model = TorrentInfoModel(
            infoHash = "4a5b6c7d8e9f",
            name = "ArchLinux-2026.iso",
            totalBytes = 1024L * 1024L * 850L,
            piecesCount = 850
        )

        assertEquals("4a5b6c7d8e9f", model.infoHash)
        assertEquals("ArchLinux-2026.iso", model.name)
        assertEquals(850, model.piecesCount)
        assertEquals(TorrentStatus.DOWNLOADING_METADATA, model.status)
        assertEquals(0L, model.downloadedBytes)
    }
}
