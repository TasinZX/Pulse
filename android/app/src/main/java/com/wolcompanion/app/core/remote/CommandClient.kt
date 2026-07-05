package com.wolcompanion.app.core.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends control commands to the Pulse PC agent (PulseRemote.ps1) over the same LAN
 * HTTP port used by remote desktop (42990). Used by the Automation features.
 *
 *   POST /command {"cmd": "..."}   -> lock / sleep / reboot / shutdown / run / clipset
 *   GET  /clipboard                 -> current PC clipboard text
 *   GET  /status                    -> {"awake": true, "idleSeconds": N}
 */
class CommandClient(private val host: String, private val port: Int = 42990) {

    data class Status(val awake: Boolean, val idleSeconds: Int)

    suspend fun lock() = command(JSONObject().put("cmd", "lock"))
    suspend fun sleep() = command(JSONObject().put("cmd", "sleep"))
    suspend fun reboot() = command(JSONObject().put("cmd", "reboot"))
    suspend fun shutdown() = command(JSONObject().put("cmd", "shutdown"))

    suspend fun runProfile(entries: List<String>) =
        command(JSONObject().put("cmd", "run").put("entries", JSONArray(entries)))

    suspend fun setClipboard(text: String) =
        command(JSONObject().put("cmd", "clipset").put("text", text))

    suspend fun getClipboard(): String? = withContext(Dispatchers.IO) {
        try {
            val conn = open("/clipboard", post = false)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            text
        } catch (e: Exception) {
            null
        }
    }

    suspend fun status(): Status? = withContext(Dispatchers.IO) {
        try {
            val conn = open("/status", post = false)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(text)
            Status(o.optBoolean("awake", true), o.optInt("idleSeconds", 0))
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun command(body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext false
        try {
            val conn = open("/command", post = true)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private fun open(path: String, post: Boolean): HttpURLConnection {
        val conn = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 4000
        if (post) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
        }
        return conn
    }
}
