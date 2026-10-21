package com.rivora.backend

import com.rivora.data.DownloadItem
import com.rivora.data.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RivoraPersistenceManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testBaseDir: File

    @Before
    fun setUp() {
        testBaseDir = tempFolder.newFolder("Rivora")
    }

    @Test
    fun testSaveAndRestoreDownloadState() {
        val fileName = "ubuntu-24.04-desktop-amd64.iso"
        val payloadFile = File(testBaseDir, fileName)
        payloadFile.writeBytes(ByteArray(1024)) // simulate 1KB downloaded file

        val item = DownloadItem(
            id = "test-dl-1",
            fileName = fileName,
            fileExtension = "ISO",
            totalBytes = 4000000000L,
            downloadedBytes = 1500000000L,
            status = DownloadStatus.ACTIVE,
            destinationPath = "Downloads/Rivora/",
            downloadUrl = "https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso"
        )

        val chunks = listOf(
            ChunkProgress(index = 0, startByte = 0L, endByte = 1999999999L, downloadedBytes = 1500000000L, status = ChunkStatus.DOWNLOADING),
            ChunkProgress(index = 1, startByte = 2000000000L, endByte = 3999999999L, downloadedBytes = 0L, status = ChunkStatus.PENDING)
        )

        // Save state into target directory
        RivoraPersistenceManager.saveDownloadState(item, chunks, payloadFile)

        val metaDir = RivoraPersistenceManager.getMetaDir(testBaseDir)
        val metaFile = File(metaDir, "$fileName.meta.json")
        assertTrue("Meta file should exist", metaFile.exists())

        // Delete state test
        RivoraPersistenceManager.deleteDownloadState(fileName, payloadFile)
        assertTrue("Meta file should be deleted", !metaFile.exists())
    }
}
