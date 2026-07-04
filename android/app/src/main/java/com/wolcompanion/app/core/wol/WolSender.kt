package com.wolcompanion.app.core.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Builds and sends Wake-on-LAN magic packets.
 *
 * A magic packet is 6 bytes of 0xFF followed by the target MAC repeated 16 times.
 * We fire it at the subnet's directed broadcast (e.g. 192.168.0.255) on UDP/9,
 * which reaches the sleeping NIC far more reliably than 255.255.255.255 on Android.
 */
object WolSender {

    /** Result of a wake attempt, so the UI can show something honest. */
    sealed interface Result {
        data class Sent(val packets: Int, val target: String) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Send the magic packet [repeat] times (a few duplicates dramatically improve
     * reliability on Wi-Fi, where a single UDP datagram is easily dropped).
     */
    suspend fun wake(
        mac: String,
        broadcastAddress: String,
        port: Int = 9,
        repeat: Int = 3,
    ): Result = withContext(Dispatchers.IO) {
        val macBytes = parseMac(mac) ?: return@withContext Result.Failed("Invalid MAC address")

        val payload = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) payload[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(macBytes, 0, payload, 6 + i * 6, 6)

        try {
            val address = InetAddress.getByName(broadcastAddress)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                var sent = 0
                repeat(repeat.coerceAtLeast(1)) {
                    socket.send(DatagramPacket(payload, payload.size, address, port))
                    sent++
                    delay(120) // small spacing so bursts aren't coalesced/dropped
                }
                Result.Sent(sent, "$broadcastAddress:$port")
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Could not send magic packet")
        }
    }

    /** Accepts AA:BB:CC:DD:EE:FF, AA-BB-..., or AABBCCDDEEFF. */
    fun parseMac(mac: String): ByteArray? {
        val hex = mac.trim().replace(":", "").replace("-", "").replace(".", "")
        if (hex.length != 12) return null
        return try {
            ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
