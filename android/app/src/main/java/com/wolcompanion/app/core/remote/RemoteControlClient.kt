package com.wolcompanion.app.core.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to Pulse's own PC remote server (pc-agent/PulseRemote.ps1) over tiny HTTP:
 *   GET /info   -> remote screen size
 *   GET /frame  -> current screen JPEG
 *   POST /input -> mouse/keyboard command
 *
 * Deliberately dependency-free (HttpURLConnection) so the app stays lean.
 */
class RemoteControlClient(host: String, port: Int = 42990) {

    private val base = "http://$host:$port"
    private val inputScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class RemoteSize(val width: Int, val height: Int)

    suspend fun fetchInfo(): RemoteSize? = withContext(Dispatchers.IO) {
        try {
            val conn = open("/info", timeout = 3000)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(text)
            RemoteSize(o.getInt("w"), o.getInt("h"))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchFrame(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val conn = open("/frame", timeout = 8000)
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    /** Fire-and-forget input command (ordering preserved via single IO scope). */
    fun send(json: JSONObject) {
        inputScope.launch {
            try {
                val conn = open("/input", timeout = 2500, post = true)
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.responseCode // trigger the request
                conn.disconnect()
            } catch (e: Exception) {
                // dropped input is acceptable; the next command supersedes it
            }
        }
    }

    // ---- convenience command builders --------------------------------------

    fun move(x: Int, y: Int) = send(cmd("m").put("x", x).put("y", y))
    fun tap(x: Int, y: Int) = send(cmd("tap").put("x", x).put("y", y))
    fun doubleTap(x: Int, y: Int) = send(cmd("double").put("x", x).put("y", y))
    fun rightTap(x: Int, y: Int) = send(cmd("rtap").put("x", x).put("y", y))
    fun click() = send(cmd("click"))
    fun rightClick() = send(cmd("rclick"))
    fun leftDown() = send(cmd("ld"))
    fun leftUp() = send(cmd("lu"))
    fun scroll(delta: Int) = send(cmd("scroll").put("d", delta))
    fun key(vk: Int) = send(cmd("key").put("vk", vk))
    fun keyDown(vk: Int) = send(cmd("kd").put("vk", vk))
    fun keyUp(vk: Int) = send(cmd("ku").put("vk", vk))
    fun text(s: String) = send(cmd("text").put("s", s))

    /** Send a key while holding the given modifier virtual-keys (Ctrl/Alt/Shift/Win). */
    fun keyWithModifiers(vk: Int, modifiers: List<Int>) {
        modifiers.forEach { keyDown(it) }
        key(vk)
        modifiers.asReversed().forEach { keyUp(it) }
    }

    private fun cmd(t: String) = JSONObject().put("t", t)

    private fun open(path: String, timeout: Int, post: Boolean = false): HttpURLConnection {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        if (post) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
        }
        return conn
    }

    fun close() {
        inputScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    companion object {
        /** Windows virtual-key codes for the on-screen keys we expose. */
        const val VK_BACK = 0x08
        const val VK_TAB = 0x09
        const val VK_RETURN = 0x0D
        const val VK_SHIFT = 0x10
        const val VK_CONTROL = 0x11
        const val VK_ALT = 0x12
        const val VK_ESCAPE = 0x1B
        const val VK_DELETE = 0x2E
        const val VK_HOME = 0x24
        const val VK_END = 0x23
        const val VK_LEFT = 0x25
        const val VK_UP = 0x26
        const val VK_RIGHT = 0x27
        const val VK_DOWN = 0x28
        const val VK_WIN = 0x5B
    }
}
