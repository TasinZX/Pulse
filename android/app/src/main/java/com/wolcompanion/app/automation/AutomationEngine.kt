package com.wolcompanion.app.automation

import android.util.Log
import com.wolcompanion.app.core.remote.CommandClient
import com.wolcompanion.app.core.wol.WolSender
import com.wolcompanion.app.data.PcDevice
import com.wolcompanion.app.data.Profile
import kotlinx.coroutines.delay

/**
 * Runs an automation action against the paired PC — the single place every
 * automation (auto-sleep, schedules, profiles, manual) routes through, so new
 * action types are added here and nowhere else.
 */
object AutomationEngine {

    private const val TAG = "Automation"

    suspend fun runAction(action: String, pc: PcDevice, profile: Profile?) {
        if (!pc.isConfigured) return
        val cmd = CommandClient(pc.ip)
        Log.i(TAG, "runAction: $action")
        when (action) {
            "wake" -> WolSender.wake(pc.mac, pc.broadcastAddress, pc.port)

            "wake_profile" -> {
                WolSender.wake(pc.mac, pc.broadcastAddress, pc.port)
                if (profile != null && profile.entries.isNotEmpty()) {
                    // Wait for the PC to come online, then launch the profile's apps.
                    if (waitUntilOnline(cmd)) cmd.runProfile(profile.entries)
                }
            }

            "sleep" -> cmd.sleep()
            "lock" -> cmd.lock()
            "reboot" -> cmd.reboot()
            "shutdown" -> cmd.shutdown()
        }
    }

    /** Poll the agent until it answers (PC booted) or the window elapses. */
    private suspend fun waitUntilOnline(cmd: CommandClient, attempts: Int = 30, intervalMs: Long = 2000): Boolean {
        repeat(attempts) {
            if (cmd.status() != null) return true
            delay(intervalMs)
        }
        return false
    }
}
