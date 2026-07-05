package com.wolcompanion.app.core.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

/** Immutable snapshot of the current Wi-Fi connection. */
data class WifiState(
    val connected: Boolean,
    val ssid: String?,
    /** First three octets of the phone's IPv4 on this network, e.g. "192.168.0". */
    val subnet: String? = null,
)

/** "192.168.0.241" -> "192.168.0". Null/blank in -> null. */
fun subnetOf(ip: String?): String? {
    if (ip.isNullOrBlank()) return null
    val parts = ip.split(".")
    return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else null
}

/**
 * Wraps ConnectivityManager so both the UI and the background service observe
 * Wi-Fi the same way. Emits a fresh [WifiState] whenever the phone binds to,
 * or drops, a Wi-Fi network.
 *
 * The [WifiState.subnet] is read from LinkProperties, which needs **no location
 * permission** — this is what lets Automatic Mode identify the home network
 * reliably in the background (SSID is unavailable to background apps).
 */
class NetworkMonitor(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Current SSID (needs location + foreground). Null if unavailable. */
    fun currentSsid(): String? {
        if (!hasLocationPermission()) return null
        @Suppress("DEPRECATION")
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val raw = wifi.connectionInfo?.ssid ?: return null
        val ssid = raw.removeSurrounding("\"")
        return if (ssid.isBlank() || ssid == "<unknown ssid>") null else ssid
    }

    /** The phone's IPv4 subnet on [network] (or the active network). No location needed. */
    fun subnetFor(network: Network?): String? {
        val net = network ?: cm.activeNetwork ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        val ipv4 = lp.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: return null
        return subnetOf(ipv4.hostAddress)
    }

    fun isWifiConnected(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Cold flow of Wi-Fi state changes. Safe to collect from a service or a ViewModel. */
    fun wifiStates(): Flow<WifiState> = callbackFlow {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        fun emit(network: Network?, connected: Boolean) {
            trySend(
                WifiState(
                    connected = connected,
                    ssid = if (connected) currentSsid() else null,
                    subnet = if (connected) subnetFor(network) else null,
                )
            )
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit(network, true)
            override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) =
                emit(network, true)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) emit(network, true)
            }
            override fun onLost(network: Network) = emit(null, false)
        }

        cm.registerNetworkCallback(request, callback)
        emit(cm.activeNetwork, isWifiConnected())

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
