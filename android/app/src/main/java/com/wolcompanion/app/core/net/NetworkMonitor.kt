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

/** Immutable snapshot of the current Wi-Fi connection. */
data class WifiState(
    val connected: Boolean,
    val ssid: String?,
)

/**
 * Wraps ConnectivityManager so both the UI and the background service observe
 * Wi-Fi the same way. Emits a fresh [WifiState] whenever the phone binds to,
 * or drops, a Wi-Fi network.
 */
class NetworkMonitor(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Current SSID, stripped of the surrounding quotes Android adds. Null if unknown. */
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

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(WifiState(connected = true, ssid = currentSsid()))
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    trySend(WifiState(connected = true, ssid = currentSsid()))
                }
            }

            override fun onLost(network: Network) {
                trySend(WifiState(connected = false, ssid = null))
            }
        }

        cm.registerNetworkCallback(request, callback)
        // Emit the initial state so collectors don't wait for the first change.
        trySend(WifiState(isWifiConnected(), currentSsid()))

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
