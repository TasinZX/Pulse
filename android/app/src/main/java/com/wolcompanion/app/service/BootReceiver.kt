package com.wolcompanion.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.automation.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts Automatic Mode and re-arms scheduled routines after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as PulseApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (app.settingsRepository.settings.first().autoWakeEnabled) {
                    AutoWakeService.start(context.applicationContext)
                }
                AlarmScheduler.rescheduleAll(context, app.automationRepository.state.first().schedules)
            } finally {
                pending.finish()
            }
        }
    }
}
