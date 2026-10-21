package com.rivora.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlProberTest {

    @Test
    fun testMagnetLinkParsing() = runBlocking {
        val magnet = "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335de7ece74fed2&dn=Ubuntu-24.04-desktop-amd64.iso"
        val result = UrlProber.probe(magnet)
        
        assertTrue(result.isSuccess)
        assertTrue(result.isDownloadable)
        assertEquals("Ubuntu-24.04-desktop-amd64.iso", result.fileName)
        assertEquals("ISO", result.fileExtension)
        assertTrue(result.isResumable)
    }

    @Test
    fun testTorrentContentUriParsing() = runBlocking {
        val contentUri = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fdebian-12.0.0-amd64-netinst.iso.torrent"
        val result = UrlProber.probe(contentUri)
        
        assertTrue(result.isSuccess)
        assertTrue(result.isDownloadable)
        assertEquals("TORRENT", result.fileExtension)
        assertEquals("application/x-bittorrent", result.contentType)
    }

    @Test
    fun testInvalidProtocolFailsGracefully() = runBlocking {
        val invalidUrl = "ftp://example.com/file.zip"
        val result = UrlProber.probe(invalidUrl)
        
        assertFalse(result.isSuccess)
        assertFalse(result.isDownloadable)
        assertTrue(result.errorMessage?.contains("Invalid URL protocol") == true)
    }
}
