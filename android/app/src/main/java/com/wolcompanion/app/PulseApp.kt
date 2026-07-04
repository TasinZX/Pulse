package com.wolcompanion.app

import android.app.Application
import com.wolcompanion.app.data.SettingsRepository
import com.wolcompanion.app.service.AutoWakeService

/** App entry point. Owns the single settings repository shared app-wide. */
class PulseApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        AutoWakeService.createChannel(this)
    }
}
