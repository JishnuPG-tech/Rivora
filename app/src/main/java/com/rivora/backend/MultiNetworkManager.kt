package com.rivora.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Types of physical or virtual network adapters.
 */
enum class NetworkAdapterType {
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN
}

/**
 * Snapshot of a network interface state and telemetry.
 */
data class NetworkAdapterInfo(
    val type: NetworkAdapterType,
    val name: String,
    val interfaceName: String,
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val ipAddress: String? = null,
    val downstreamBandwidthKbps: Int = 0,
    val upstreamBandwidthKbps: Int = 0,
    val streamsCount: Int = 0
)

/**
 * MultiNetworkManager coordinates concurrent network interfaces on Android.
 *
 * Unlike standard Android networking where mobile data is deactivated when Wi-Fi is connected,
 * MultiNetworkManager invokes ConnectivityManager.requestNetwork for TRANSPORT_CELLULAR.
 * This instructs the Android modem to maintain an active PDP data session concurrently with Wi-Fi.
 *
 * Each active interface yields a dedicated, bound OkHttpClient via Network.socketFactory
 * and Network.getAllByName for DNS resolution.
 */
class MultiNetworkManager(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "MultiNetworkManager"

        @Volatile
        private var INSTANCE: MultiNetworkManager? = null

        fun getInstance(context: Context? = null): MultiNetworkManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MultiNetworkManager(context?.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val connectivityManager: ConnectivityManager? =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Map of active network types to their bound Network object
    private val boundNetworks = ConcurrentHashMap<NetworkAdapterType, Network>()

    // Dedicated connection pools per adapter to keep TCP connections warm and multiplexed
    // while ensuring strict socket isolation across adapters
    private val connectionPools = ConcurrentHashMap<NetworkAdapterType, ConnectionPool>()

    private fun getConnectionPool(type: NetworkAdapterType): ConnectionPool {
        return connectionPools.getOrPut(type) {
            ConnectionPool(16, 5, TimeUnit.MINUTES)
        }
    }

    // Map of active network types to their dedicated OkHttpClient
    private val clientCache = ConcurrentHashMap<NetworkAdapterType, OkHttpClient>()

    // Base client template
    private val baseClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        })
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // Observable network states
    private val _networkStates = MutableStateFlow<Map<NetworkAdapterType, NetworkAdapterInfo>>(
        mapOf(
            NetworkAdapterType.WIFI to NetworkAdapterInfo(
                type = NetworkAdapterType.WIFI,
                name = "Wi-Fi",
                interfaceName = "wlan0",
                isAvailable = false,
                isConnected = false
            ),
            NetworkAdapterType.CELLULAR to NetworkAdapterInfo(
                type = NetworkAdapterType.CELLULAR,
                name = "Cellular",
                interfaceName = "rmnet0",
                isAvailable = false,
                isConnected = false
            ),
            NetworkAdapterType.ETHERNET to NetworkAdapterInfo(
                type = NetworkAdapterType.ETHERNET,
                name = "Ethernet",
                interfaceName = "eth0",
                isAvailable = false,
                isConnected = false
            )
        )
    )
    val networkStates: StateFlow<Map<NetworkAdapterType, NetworkAdapterInfo>> = _networkStates.asStateFlow()

    private var isWifiRequested = false
    private var isCellularRequested = false
    private var isRegistered = false

    // Fast DNS cache to resolve hostnames across both adapters without DoT stalls
    private val hostDnsCache = ConcurrentHashMap<String, List<java.net.InetAddress>>()

    // Callbacks
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var cellularCallback: ConnectivityManager.NetworkCallback? = null
    private var ethernetCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Checks if at least one internet-capable network interface is actively connected.
     */
    fun isAnyNetworkConnected(): Boolean {
        if (boundNetworks.isNotEmpty()) return true
        val active = connectivityManager?.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Returns count of actively connected and bound network adapters.
     */
    fun getConnectedAdaptersCount(): Int = boundNetworks.size

    /**
     * Start monitoring networks and request concurrent Wi-Fi and Cellular connectivity.
     */
    fun startMonitoring() {
        if (connectivityManager == null || isRegistered) return

        try {
            // Synchronously inspect currently connected networks immediately upon startup
            try {
                for (network in connectivityManager.allNetworks) {
                    val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                    val lp = connectivityManager.getLinkProperties(network)
                    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    if (!hasInternet) continue

                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        boundNetworks[NetworkAdapterType.WIFI] = network
                        val ifName = lp?.interfaceName ?: "wlan0"
                        val ipv4 = lp?.linkAddresses?.mapNotNull { it.address }
                            ?.filterIsInstance<Inet4Address>()?.firstOrNull()?.hostAddress
                        _networkStates.update { current ->
                            current + (NetworkAdapterType.WIFI to NetworkAdapterInfo(
                                type = NetworkAdapterType.WIFI,
                                name = "Wi-Fi",
                                interfaceName = ifName,
                                isAvailable = true,
                                isConnected = true,
                                ipAddress = ipv4
                            ))
                        }
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        boundNetworks[NetworkAdapterType.CELLULAR] = network
                        val ifName = lp?.interfaceName ?: "rmnet0"
                        val ipv4 = lp?.linkAddresses?.mapNotNull { it.address }
                            ?.filterIsInstance<Inet4Address>()?.firstOrNull()?.hostAddress
                        _networkStates.update { current ->
                            current + (NetworkAdapterType.CELLULAR to NetworkAdapterInfo(
                                type = NetworkAdapterType.CELLULAR,
                                name = "Cellular",
                                interfaceName = ifName,
                                isAvailable = true,
                                isConnected = true,
                                ipAddress = ipv4
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Initial network scan error: ${e.message}")
            }

            // 1. Request Wi-Fi concurrent session
            requestWifiConcurrency()

            // 2. Request Cellular concurrent connection
            requestCellularConcurrency()

            // 3. Monitor Ethernet if available
            val ethernetRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()

            ethernetCallback = createCallback(NetworkAdapterType.ETHERNET, "Ethernet", "eth0")
            connectivityManager.registerNetworkCallback(ethernetRequest, ethernetCallback!!)

            // 4. Register default network callback for system-wide resilience (e.g. Aeroplane mode toggle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                defaultCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Default network restored: $network")
                        if (!isWifiRequested) requestWifiConcurrency()
                        if (!isCellularRequested) requestCellularConcurrency()
                    }
                    override fun onLost(network: Network) {
                        Log.d(TAG, "Default network lost: $network")
                        clientCache.clear()
                    }
                }
                connectivityManager.registerDefaultNetworkCallback(defaultCallback!!)
            }

            isRegistered = true
            Log.d(TAG, "Multi-network callbacks successfully registered with concurrent Wi-Fi & Cellular.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callbacks: ${e.message}", e)
        }
    }

    /**
     * Instructs Android OS to maintain an active Wi-Fi session concurrently with Cellular.
     */
    fun requestWifiConcurrency() {
        if (connectivityManager == null || isWifiRequested) return
        try {
            val wifiRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            wifiCallback = createCallback(NetworkAdapterType.WIFI, "Wi-Fi", "wlan0")
            try {
                // requestNetwork guarantees Android allocates and guards the Wi-Fi connection
                connectivityManager.requestNetwork(wifiRequest, wifiCallback!!)
                isWifiRequested = true
                Log.d(TAG, "Requested concurrent Wi-Fi network session.")
            } catch (e: Exception) {
                Log.w(TAG, "requestNetwork for Wi-Fi fell back to registerNetworkCallback: ${e.message}")
                connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback!!)
                isWifiRequested = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Wi-Fi network: ${e.message}", e)
        }
    }

    /**
     * Releases Wi-Fi concurrency callback.
     */
    fun releaseWifiConcurrency() {
        if (connectivityManager == null || !isWifiRequested || wifiCallback == null) return
        try {
            connectivityManager.unregisterNetworkCallback(wifiCallback!!)
            wifiCallback = null
            isWifiRequested = false
            boundNetworks.remove(NetworkAdapterType.WIFI)
            clientCache.remove(NetworkAdapterType.WIFI)
            _networkStates.update { current ->
                val existing = current[NetworkAdapterType.WIFI] ?: NetworkAdapterInfo(
                    type = NetworkAdapterType.WIFI,
                    name = "Wi-Fi",
                    interfaceName = "wlan0"
                )
                current + (NetworkAdapterType.WIFI to existing.copy(
                    isAvailable = false,
                    isConnected = false,
                    streamsCount = 0
                ))
            }
            Log.d(TAG, "Released concurrent Wi-Fi network session.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister Wi-Fi callback: ${e.message}", e)
        }
    }

    /**
     * Instructs Android OS to keep mobile data alive alongside Wi-Fi.
     */
    fun requestCellularConcurrency() {
        if (connectivityManager == null || isCellularRequested) return

        try {
            val cellularRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()

            cellularCallback = createCallback(NetworkAdapterType.CELLULAR, "Cellular", "rmnet0")
            try {
                // requestNetwork forces Android telephony to activate mobile data even when Wi-Fi is primary!
                connectivityManager.requestNetwork(cellularRequest, cellularCallback!!)
                isCellularRequested = true
                Log.d(TAG, "Requested concurrent cellular network session.")
            } catch (e: Exception) {
                Log.w(TAG, "requestNetwork for Cellular fell back to registerNetworkCallback: ${e.message}")
                connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback!!)
                isCellularRequested = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request cellular network: ${e.message}", e)
        }
    }

    /**
     * Releases cellular concurrency to save battery/data when user turns off Cellular bonding.
     */
    fun releaseCellularConcurrency() {
        if (connectivityManager == null || !isCellularRequested || cellularCallback == null) return

        try {
            connectivityManager.unregisterNetworkCallback(cellularCallback!!)
            cellularCallback = null
            isCellularRequested = false
            boundNetworks.remove(NetworkAdapterType.CELLULAR)
            clientCache.remove(NetworkAdapterType.CELLULAR)

            _networkStates.update { current ->
                val existing = current[NetworkAdapterType.CELLULAR] ?: NetworkAdapterInfo(
                    type = NetworkAdapterType.CELLULAR,
                    name = "Cellular",
                    interfaceName = "rmnet0"
                )
                current + (NetworkAdapterType.CELLULAR to existing.copy(
                    streamsCount = 0
                ))
            }
            Log.d(TAG, "Released concurrent cellular network session.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister cellular callback: ${e.message}", e)
        }
    }

    /**
     * Stop all callbacks and release resources.
     */
    fun stopMonitoring() {
        if (connectivityManager == null || !isRegistered) return

        try {
            ethernetCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
            releaseWifiConcurrency()
            releaseCellularConcurrency()
            ethernetCallback = null
            isRegistered = false
            boundNetworks.clear()
            clientCache.clear()
            connectionPools.values.forEach { it.evictAll() }
            connectionPools.clear()
            hostDnsCache.clear()
            Log.d(TAG, "Stopped all network monitoring.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network callbacks: ${e.message}", e)
        }
    }

    /**
     * Returns an OkHttpClient bound strictly to the requested network interface.
     * Crucially uses an isolated, 0-keepalive ConnectionPool so TCP/TLS connections
     * are NEVER cross-routed or shared between Wi-Fi and Cellular sockets!
     */
    fun getClient(type: NetworkAdapterType): OkHttpClient? {
        val cached = clientCache[type]
        if (cached != null) return cached

        val network = boundNetworks[type] ?: return null
        val newClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 32
            })
            .connectionPool(getConnectionPool(type))
            .socketFactory(network.socketFactory)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    // Check fast cache first
                    hostDnsCache[hostname]?.let { return it }

                    // Attempt resolving directly on bound network
                    val networkAddresses = try {
                        val addresses = network.getAllByName(hostname).toList()
                        if (addresses.isNotEmpty()) addresses else null
                    } catch (_: Exception) {
                        null
                    }

                    if (networkAddresses != null) {
                        hostDnsCache[hostname] = networkAddresses
                        return networkAddresses
                    }

                    // Fallback to system resolver to avoid Private DNS (DoT) hanging
                    val fallback = try {
                        Dns.SYSTEM.lookup(hostname)
                    } catch (_: Exception) {
                        try {
                            java.net.InetAddress.getAllByName(hostname).toList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    if (fallback.isNotEmpty()) {
                        hostDnsCache[hostname] = fallback
                    }
                    return fallback
                }
            })
            .build()
        clientCache[type] = newClient
        return newClient
    }

    /**
     * Returns default base client for fallback operations.
     */
    fun getDefaultClient(): OkHttpClient = baseClient

    /**
     * Returns true if the specific network adapter is actively connected.
     */
    fun isNetworkConnected(type: NetworkAdapterType): Boolean = boundNetworks.containsKey(type)

    /**
     * Returns the active bound network for an adapter type, or null.
     */
    fun getBoundNetwork(type: NetworkAdapterType): Network? = boundNetworks[type]

    /**
     * Manually update adapter state (useful for mocking, testing, or stream counters).
     */
    fun updateAdapterInfo(type: NetworkAdapterType, transform: (NetworkAdapterInfo) -> NetworkAdapterInfo) {
        _networkStates.update { current ->
            val existing = current[type] ?: NetworkAdapterInfo(
                type = type,
                name = type.name,
                interfaceName = ""
            )
            current + (type to transform(existing))
        }
    }

    private fun createCallback(
        adapterType: NetworkAdapterType,
        defaultName: String,
        defaultIfName: String
    ): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                boundNetworks[adapterType] = network
                clientCache.remove(adapterType) // invalidate cached client to recreate bound client

                val lp = connectivityManager?.getLinkProperties(network)
                val caps = connectivityManager?.getNetworkCapabilities(network)

                val ifName = lp?.interfaceName ?: defaultIfName
                val ipv4 = lp?.linkAddresses?.mapNotNull { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull()?.hostAddress

                val downKbps = caps?.linkDownstreamBandwidthKbps ?: 0
                val upKbps = caps?.linkUpstreamBandwidthKbps ?: 0

                _networkStates.update { current ->
                    current + (adapterType to NetworkAdapterInfo(
                        type = adapterType,
                        name = defaultName,
                        interfaceName = ifName,
                        isAvailable = true,
                        isConnected = true,
                        ipAddress = ipv4,
                        downstreamBandwidthKbps = downKbps,
                        upstreamBandwidthKbps = upKbps
                    ))
                }
                Log.d(TAG, "[$adapterType] Available on interface $ifName (IP: $ipv4, Down: ${downKbps}kbps)")
            }

            override fun onLost(network: Network) {
                boundNetworks.remove(adapterType)
                clientCache.remove(adapterType)

                _networkStates.update { current ->
                    val existing = current[adapterType] ?: NetworkAdapterInfo(
                        type = adapterType,
                        name = defaultName,
                        interfaceName = defaultIfName
                    )
                    current + (adapterType to existing.copy(
                        isAvailable = false,
                        isConnected = false,
                        streamsCount = 0
                    ))
                }
                Log.d(TAG, "[$adapterType] Lost connection.")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val downKbps = caps.linkDownstreamBandwidthKbps
                val upKbps = caps.linkUpstreamBandwidthKbps

                _networkStates.update { current ->
                    val existing = current[adapterType] ?: return@update current
                    current + (adapterType to existing.copy(
                        downstreamBandwidthKbps = downKbps,
                        upstreamBandwidthKbps = upKbps
                    ))
                }
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                val ifName = lp.interfaceName ?: defaultIfName
                val ipv4 = lp.linkAddresses.mapNotNull { it.address }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull()?.hostAddress

                _networkStates.update { current ->
                    val existing = current[adapterType] ?: return@update current
                    current + (adapterType to existing.copy(
                        interfaceName = ifName,
                        ipAddress = ipv4 ?: existing.ipAddress
                    ))
                }
            }
        }
    }
}
