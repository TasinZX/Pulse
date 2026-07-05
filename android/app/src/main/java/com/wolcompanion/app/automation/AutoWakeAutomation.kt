package com.wolcompanion.app.automation

import android.util.Log
import com.wolcompanion.app.core.net.PcReachability
import com.wolcompanion.app.core.net.WifiState
import com.wolcompanion.app.core.net.subnetOf
import com.wolcompanion.app.core.wol.WolSender
import com.wolcompanion.app.data.AppSettings
import kotlinx.coroutines.delay

/**
 * Wakes the configured PC the moment the phone joins the saved home Wi-Fi.
 *
 * Reliability guards:
 *  - Fires only on a *transition into* the home SSID, not on every capability tick.
 *  - Debounces: won't re-fire within [cooldownMs] (brief Wi-Fi drops won't spam).
 *  - Skips the wake entirely if the PC already answers on the LAN.
 */
class AutoWakeAutomation(
    private val onWake: (String) -> Unit = {},   // hook for a user-facing notification
) : Automation {

    override val id = "auto_wake"

    private var lastWakeAt = 0L
    private val cooldownMs = 60_000L

    override fun isEnabled(settings: AppSettings): Boolean =
        settings.autoWakeEnabled && settings.homeSsid.isNotBlank() && settings.pc.isConfigured

    override suspend fun onWifiEvent(
        previous: WifiState?,
        current: WifiState,
        settings: AppSettings,
    ) {
        if (!isEnabled(settings)) return

        // "Home" is matched by SSID when available (foreground), OR by subnet, which
        // LinkProperties exposes with no location permission and works in the background.
        val homeSubnet = subnetOf(settings.pc.ip)
        fun WifiState.isHome(): Boolean {
            if (!connected) return false
            if (settings.homeSsid.isNotBlank() && ssid.equals(settings.homeSsid, ignoreCase = true)) return true
            return homeSubnet != null && subnet == homeSubnet
        }

        val joinedHome = current.isHome()
        val wasHome = previous?.isHome() == true

        // Only act on a fresh arrival onto the home network.
        if (!joinedHome || wasHome) return

        val now = System.currentTimeMillis()
        if (now - lastWakeAt < cooldownMs) {
            Log.d(TAG, "Skip: within cooldown")
            return
        }

        // Don't wake a PC that's already on.
        if (settings.pc.ip.isNotBlank() && PcReachability.isAwake(settings.pc.ip)) {
            Log.d(TAG, "Skip: PC already awake")
            lastWakeAt = now
            return
        }

        lastWakeAt = now
        onWake(settings.pc.name.ifBlank { "your PC" })

        // Fire a burst of magic packets over a short window. Right after a Wi-Fi
        // (re)connect the link is still settling, so a single packet is easily lost —
        // repeating for a few seconds makes arrival-wake reliable.
        repeat(5) { attempt ->
            val result = WolSender.wake(
                mac = settings.pc.mac,
                broadcastAddress = settings.pc.broadcastAddress,
                port = settings.pc.port,
            )
            Log.i(TAG, "Auto-wake attempt ${attempt + 1}: $result")
            // Stop early if the PC has come up.
            if (settings.pc.ip.isNotBlank() && PcReachability.isAwake(settings.pc.ip)) return
            delay(2000)
        }
    }

    companion object {
        private const val TAG = "AutoWake"
    }
}
