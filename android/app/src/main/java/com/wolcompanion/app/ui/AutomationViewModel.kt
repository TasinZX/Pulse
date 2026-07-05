package com.wolcompanion.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.automation.AlarmScheduler
import com.wolcompanion.app.automation.AutomationEngine
import com.wolcompanion.app.core.remote.CommandClient
import com.wolcompanion.app.data.AutoSleepConfig
import com.wolcompanion.app.data.AutomationState
import com.wolcompanion.app.data.ClipboardConfig
import com.wolcompanion.app.data.PcDevice
import com.wolcompanion.app.data.Profile
import com.wolcompanion.app.data.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AutomationViewModel(app: Application) : AndroidViewModel(app) {

    private val autoRepo = (app as PulseApp).automationRepository
    private val settingsRepo = (app as PulseApp).settingsRepository

    val state: StateFlow<AutomationState> =
        autoRepo.state.stateIn(viewModelScope, SharingStarted.Eagerly, AutomationState())

    val pc: StateFlow<PcDevice> =
        settingsRepo.settings.map { it.pc }.stateIn(viewModelScope, SharingStarted.Eagerly, PcDevice())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }
    private fun toast(t: String) { _message.value = t }

    fun newId(): String = UUID.randomUUID().toString()

    // ---- Auto-sleep --------------------------------------------------------

    fun setAutoSleep(cfg: AutoSleepConfig) = viewModelScope.launch {
        autoRepo.update { it.copy(autoSleep = cfg) }
    }

    // ---- Clipboard ---------------------------------------------------------

    fun setClipboardAutoPush(enabled: Boolean) = viewModelScope.launch {
        autoRepo.update { it.copy(clipboard = ClipboardConfig(enabled)) }
    }

    fun pushClipboard() = viewModelScope.launch {
        val host = pc.value.ip
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString()
        if (text.isNullOrEmpty()) { toast("Phone clipboard is empty"); return@launch }
        toast(if (CommandClient(host).setClipboard(text)) "Sent clipboard to PC" else "Couldn't reach PC")
    }

    fun pullClipboard() = viewModelScope.launch {
        val text = CommandClient(pc.value.ip).getClipboard()
        if (text == null) { toast("Couldn't reach PC"); return@launch }
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PC clipboard", text))
        toast("Copied PC clipboard to phone")
    }

    // ---- Profiles ----------------------------------------------------------

    fun saveProfile(profile: Profile) = viewModelScope.launch {
        autoRepo.update { s ->
            val others = s.profiles.filterNot { it.id == profile.id }
            s.copy(profiles = others + profile)
        }
    }

    fun deleteProfile(id: String) = viewModelScope.launch {
        autoRepo.update { s -> s.copy(profiles = s.profiles.filterNot { it.id == id }) }
    }

    fun runProfileNow(profile: Profile) = viewModelScope.launch {
        if (profile.entries.isEmpty()) { toast("Profile has no apps"); return@launch }
        toast(if (CommandClient(pc.value.ip).runProfile(profile.entries)) "Launching ${profile.name}…" else "Couldn't reach PC")
    }

    fun wakeWithProfile(profile: Profile) = viewModelScope.launch {
        toast("Waking + launching ${profile.name}…")
        AutomationEngine.runAction("wake_profile", pc.value, profile)
    }

    // ---- Schedules ---------------------------------------------------------

    fun saveSchedule(schedule: Schedule) = viewModelScope.launch {
        autoRepo.update { s ->
            val others = s.schedules.filterNot { it.id == schedule.id }
            s.copy(schedules = others + schedule)
        }
        AlarmScheduler.scheduleOne(getApplication(), schedule)
    }

    fun toggleSchedule(schedule: Schedule, enabled: Boolean) =
        saveSchedule(schedule.copy(enabled = enabled))

    fun deleteSchedule(id: String) = viewModelScope.launch {
        val toRemove = state.value.schedules.firstOrNull { it.id == id }
        autoRepo.update { s -> s.copy(schedules = s.schedules.filterNot { it.id == id }) }
        toRemove?.let { AlarmScheduler.scheduleOne(getApplication(), it.copy(enabled = false)) }
    }
}
