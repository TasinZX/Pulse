package com.wolcompanion.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wolcompanion.app.PulseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts Automatic Mode after a reboot if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val repo = (context.applicationContext as PulseApp).settingsRepository
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (repo.settings.first().autoWakeEnabled) {
                    AutoWakeService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
