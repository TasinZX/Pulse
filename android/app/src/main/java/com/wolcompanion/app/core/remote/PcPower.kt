package com.wolcompanion.app.core.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remote power control for the PC, via the Pulse agent's HTTP server (PulseRemote.ps1).
 * Reuses the same port as the remote-desktop server (42990).
 */
object PcPower {

    /** Ask the PC to shut down. Returns true if the command was accepted. */
    suspend fun shutdown(host: String, port: Int = 42990): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext false
        try {
            val conn = URL("http://$host:$port/shutdown").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true
            conn.outputStream.use { it.write(ByteArray(0)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
