package com.rivora.backend

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiNetworkManagerTest {

    @Test
    fun testInitialNetworkStates() = runBlocking {
        val manager = MultiNetworkManager()
        val states = manager.networkStates.first()

        assertTrue(states.containsKey(NetworkAdapterType.WIFI))
        assertTrue(states.containsKey(NetworkAdapterType.CELLULAR))
        assertTrue(states.containsKey(NetworkAdapterType.ETHERNET))

        val wifi = states[NetworkAdapterType.WIFI]
        assertNotNull(wifi)
        assertEquals("Wi-Fi", wifi?.name)
        assertEquals("wlan0", wifi?.interfaceName)
        assertEquals(false, wifi?.isConnected)
    }

    @Test
    fun testAdapterStateUpdate() = runBlocking {
        val manager = MultiNetworkManager()

        manager.updateAdapterInfo(NetworkAdapterType.WIFI) {
            it.copy(
                isConnected = true,
                isAvailable = true,
                ipAddress = "192.168.1.105",
                streamsCount = 3
            )
        }

        val states = manager.networkStates.first()
        val wifi = states[NetworkAdapterType.WIFI]

        assertNotNull(wifi)
        assertEquals(true, wifi?.isConnected)
        assertEquals("192.168.1.105", wifi?.ipAddress)
        assertEquals(3, wifi?.streamsCount)
    }

    @Test
    fun testGetClientFallback() {
        val manager = MultiNetworkManager()
        val wifiClient = manager.getClient(NetworkAdapterType.WIFI)
        val cellularClient = manager.getClient(NetworkAdapterType.CELLULAR)

        assertNotNull(wifiClient)
        assertNotNull(cellularClient)
        // Verify timeout configuration
        assertEquals(15000, wifiClient.connectTimeoutMillis)
        assertEquals(30000, wifiClient.readTimeoutMillis)
    }
}
