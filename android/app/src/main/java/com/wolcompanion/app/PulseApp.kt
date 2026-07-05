package com.wolcompanion.app

import android.app.Application
import com.wolcompanion.app.data.AutomationRepository
import com.wolcompanion.app.data.SettingsRepository
import com.wolcompanion.app.service.AutoWakeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App entry point. Owns the single settings repository shared app-wide. */
class PulseApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var automationRepository: AutomationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        automationRepository = AutomationRepository(this)
        AutoWakeService.createChannel(this)

        // Resume Automatic Mode if it was enabled but the process had been killed.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (settingsRepository.settings.first().autoWakeEnabled) {
                AutoWakeService.start(this@PulseApp)
            }
        }
    }
}
