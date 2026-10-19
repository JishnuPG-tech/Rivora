package com.rivora.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlProberTest {

    @Test
    fun testMagnetLinkParsing() = runBlocking {
        val magnet = "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335de7ece74fed2&dn=Ubuntu-24.04-desktop-amd64.iso"
        val result = UrlProber.probe(magnet)
        
        assertTrue(result.isSuccess)
        assertEquals("Ubuntu-24.04-desktop-amd64.iso", result.fileName)
        assertEquals("ISO", result.fileExtension)
        assertTrue(result.isResumable)
    }

    @Test
    fun testInvalidProtocolFailsGracefully() = runBlocking {
        val invalidUrl = "ftp://example.com/file.zip"
        val result = UrlProber.probe(invalidUrl)
        
        assertEquals(false, result.isSuccess)
        assertTrue(result.errorMessage?.contains("Invalid URL protocol") == true)
    }
}
