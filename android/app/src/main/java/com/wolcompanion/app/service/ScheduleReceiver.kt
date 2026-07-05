package com.wolcompanion.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.automation.AlarmScheduler
import com.wolcompanion.app.automation.AutomationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fires when a scheduled routine is due: runs its action, then reschedules it. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext as PulseApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val state = app.automationRepository.state.first()
                val schedule = state.schedules.firstOrNull { it.id == id } ?: return@launch
                if (!schedule.enabled) return@launch

                val pc = app.settingsRepository.settings.first().pc

                // Optional "only if the PC has been idle for N minutes" condition
                // (meaningful for sleep/lock/shutdown actions).
                if (schedule.requireIdleMinutes > 0) {
                    val status = com.wolcompanion.app.core.remote.CommandClient(pc.ip).status()
                    val idleMin = (status?.idleSeconds ?: 0) / 60
                    if (idleMin < schedule.requireIdleMinutes) return@launch
                }

                val profile = state.profiles.firstOrNull { it.id == schedule.profileId }
                AutomationEngine.runAction(schedule.action, pc, profile)
            } finally {
                // Reschedule this rule's next occurrence.
                app.automationRepository.state.first().schedules
                    .firstOrNull { it.id == id }
                    ?.let { AlarmScheduler.scheduleOne(context, it) }
                pending.finish()
            }
        }
    }
}
