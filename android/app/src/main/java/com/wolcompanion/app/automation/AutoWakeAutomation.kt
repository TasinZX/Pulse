package com.wolcompanion.app.automation

import android.util.Log
import com.wolcompanion.app.core.net.PcReachability
import com.wolcompanion.app.core.net.WifiState
import com.wolcompanion.app.core.wol.WolSender
import com.wolcompanion.app.data.AppSettings

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

        val joinedHome = current.connected &&
            current.ssid.equals(settings.homeSsid, ignoreCase = true)
        val wasHome = previous?.connected == true &&
            previous.ssid.equals(settings.homeSsid, ignoreCase = true)

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
        val result = WolSender.wake(
            mac = settings.pc.mac,
            broadcastAddress = settings.pc.broadcastAddress,
            port = settings.pc.port,
        )
        Log.i(TAG, "Auto-wake result: $result")
        if (result is WolSender.Result.Sent) {
            onWake(settings.pc.name.ifBlank { "your PC" })
        }
    }

    companion object {
        private const val TAG = "AutoWake"
    }
}
