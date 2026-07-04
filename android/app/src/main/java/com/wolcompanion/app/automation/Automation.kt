package com.wolcompanion.app.automation

import com.wolcompanion.app.core.net.WifiState
import com.wolcompanion.app.data.AppSettings

/**
 * A pluggable automation triggered by network events.
 *
 * The auto-wake feature is the first implementation. Future automations
 * (shutdown-on-leave, notify-on-arrive, etc.) implement this same interface
 * and register in [AutomationRegistry] — the service loop never changes.
 */
interface Automation {
    val id: String

    /** Whether this automation is turned on given current settings. */
    fun isEnabled(settings: AppSettings): Boolean

    /** Called on every Wi-Fi state change while the service runs. */
    suspend fun onWifiEvent(previous: WifiState?, current: WifiState, settings: AppSettings)
}
