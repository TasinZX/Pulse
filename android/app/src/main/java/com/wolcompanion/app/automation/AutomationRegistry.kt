package com.wolcompanion.app.automation

/**
 * Central list of active automations. Add future automations here and the
 * background service picks them up with zero other changes.
 */
object AutomationRegistry {
    fun automations(onWake: (String) -> Unit): List<Automation> = listOf(
        AutoWakeAutomation(onWake = onWake),
        // e.g. ShutdownOnLeaveAutomation(), NotifyOnArriveAutomation(), ...
    )
}
