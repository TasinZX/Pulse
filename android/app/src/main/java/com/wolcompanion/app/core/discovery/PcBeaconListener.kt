package com.wolcompanion.app.core.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.wolcompanion.app.data.PcDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Discovers PCs on the LAN by listening for beacons broadcast by the Pulse Agent
 * (pc-agent/PulseAgent.ps1) on UDP 42999. Each beacon is a small JSON blob with the
 * PC's name/MAC/IP/broadcast — everything the app needs to pair and wake, with zero
 * manual entry.
 */
class PcBeaconListener(private val context: Context) {

    /** Listen for [durationMs] and return the unique PCs heard, keyed by MAC. */
    suspend fun scan(durationMs: Long = 4000): List<PcDevice> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Needed on many devices to receive broadcast/multicast frames while scanning.
        val lock = wifi.createMulticastLock("pulse-discovery").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val found = LinkedHashMap<String, PcDevice>()
        try {
            DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = 500
                bind(InetSocketAddress(PORT))
            }.use { socket ->
                val deadline = System.currentTimeMillis() + durationMs
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        parse(String(packet.data, 0, packet.length))?.let { pc ->
                            found[pc.mac.uppercase()] = pc
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // keep looping until the overall deadline
                    }
                }
            }
        } catch (e: Exception) {
            // Return whatever we found; the UI shows an empty-state with guidance.
        } finally {
            runCatching { lock.release() }
        }
        found.values.toList()
    }

    private fun parse(json: String): PcDevice? = try {
        val o = JSONObject(json)
        if (o.optString("pulse") != "v1") null
        else PcDevice(
            name = o.optString("name").ifBlank { "PC" },
            mac = o.optString("mac"),
            broadcastAddress = o.optString("broadcast").ifBlank { "255.255.255.255" },
            ip = o.optString("ip"),
            port = 9,
        ).takeIf { it.mac.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val PORT = 42999
    }
}
