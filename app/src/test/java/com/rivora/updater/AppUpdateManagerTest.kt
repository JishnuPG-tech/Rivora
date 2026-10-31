package com.rivora.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testSemanticVersionComparison() {
        // Newer patch versions
        assertTrue(AppUpdateManager.isNewerVersion("1.1.4", "1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.4", "1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.0", "v1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))

        // Multi-digit version components (1.10 > 1.2)
        assertTrue(AppUpdateManager.isNewerVersion("1.10.0", "1.2.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.10", "1.1.3"))

        // Equal versions
        assertFalse(AppUpdateManager.isNewerVersion("1.1.3", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.1.3", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.3", "v1.1.3"))

        // Older versions
        assertFalse(AppUpdateManager.isNewerVersion("1.1.2", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.9", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("0.9.0", "1.1.3"))

        // Versions with suffixes / prereleases
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-rc1", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.2-hotfix", "1.1.3"))
    }
}
