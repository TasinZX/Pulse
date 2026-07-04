package com.wolcompanion.app.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cheap "is the PC awake?" probe used to:
 *  - skip a redundant wake when the PC is already on, and
 *  - confirm a Test Wake actually brought the machine up.
 *
 * We try a TCP connect to common always-open Windows ports; ICMP ping is
 * unreliable/blocked from Android. Any successful connect (or an immediate
 * connection-refused, which still proves the host is up) counts as awake.
 */
object PcReachability {

    private val probePorts = intArrayOf(445, 135, 3389, 139, 80)

    /** Single reachability check. */
    suspend fun isAwake(host: String, timeoutMs: Int = 700): Boolean =
        withContext(Dispatchers.IO) {
            if (host.isBlank()) return@withContext false
            for (port in probePorts) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), timeoutMs)
                    }
                    return@withContext true
                } catch (e: java.net.ConnectException) {
                    // Host answered with RST -> it's up, just not listening there.
                    return@withContext true
                } catch (e: Exception) {
                    // timeout / unreachable -> try next port
                }
            }
            false
        }

    /**
     * Poll until the host comes up or [attempts] run out. Used after a Test Wake
     * to confirm the PC actually booted.
     */
    suspend fun waitUntilAwake(
        host: String,
        attempts: Int = 20,
        intervalMs: Long = 2000,
    ): Boolean {
        repeat(attempts) {
            if (isAwake(host)) return true
            delay(intervalMs)
        }
        return false
    }
}
