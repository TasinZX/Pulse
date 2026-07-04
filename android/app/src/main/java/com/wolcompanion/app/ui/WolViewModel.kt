package com.wolcompanion.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.core.net.NetworkMonitor
import com.wolcompanion.app.core.net.PcReachability
import com.wolcompanion.app.core.wol.WolSender
import com.wolcompanion.app.data.AppSettings
import com.wolcompanion.app.data.PcDevice
import com.wolcompanion.app.service.AutoWakeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Transient status of the last wake action, surfaced as a toast/pill in the UI. */
sealed interface WakeStatus {
    data object Idle : WakeStatus
    data object Sending : WakeStatus
    data class Sent(val target: String) : WakeStatus
    data object Verifying : WakeStatus
    data class Awake(val name: String) : WakeStatus
    data class Message(val text: String, val isError: Boolean = false) : WakeStatus
}

class WolViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as PulseApp).settingsRepository
    private val monitor = NetworkMonitor(app)
    private val discovery = com.wolcompanion.app.core.discovery.PcBeaconListener(app)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _wakeStatus = MutableStateFlow<WakeStatus>(WakeStatus.Idle)
    val wakeStatus: StateFlow<WakeStatus> = _wakeStatus.asStateFlow()

    /** Live Wi-Fi snapshot for the home screen and SSID capture. */
    val wifi: StateFlow<com.wolcompanion.app.core.net.WifiState> =
        monitor.wifiStates().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.wolcompanion.app.core.net.WifiState(false, null),
        )

    fun currentSsid(): String? = monitor.currentSsid()

    // ---- Auto-discovery ----------------------------------------------------

    private val _discovered = MutableStateFlow<List<PcDevice>>(emptyList())
    val discovered: StateFlow<List<PcDevice>> = _discovered.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun scanForPcs() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val results = discovery.scan(durationMs = 4000)
            // Merge with anything already found so repeated scans accumulate.
            val merged = (results + _discovered.value).associateBy { it.mac.uppercase() }
            _discovered.value = merged.values.toList()
            _isScanning.value = false
        }
    }

    /** One-tap pairing: save the PC and auto-capture the current Wi-Fi as "home". */
    fun pair(pc: PcDevice) {
        viewModelScope.launch {
            repo.savePc(pc)
            currentSsid()?.let { repo.saveHomeSsid(it) }
            repo.setSetupComplete(true)
        }
    }

    // ---- Manual Mode -------------------------------------------------------

    fun wakeNow() {
        val pc = settings.value.pc
        if (!pc.isConfigured) {
            _wakeStatus.value = WakeStatus.Message("Finish setup first", isError = true)
            return
        }
        viewModelScope.launch {
            _wakeStatus.value = WakeStatus.Sending
            when (val r = WolSender.wake(pc.mac, pc.broadcastAddress, pc.port)) {
                is WolSender.Result.Sent -> _wakeStatus.value = WakeStatus.Sent(r.target)
                is WolSender.Result.Failed ->
                    _wakeStatus.value = WakeStatus.Message(r.reason, isError = true)
            }
        }
    }

    /** Test Wake: send, then poll the PC to confirm it actually came up. */
    fun testWake() {
        val pc = settings.value.pc
        if (!pc.isConfigured) {
            _wakeStatus.value = WakeStatus.Message("Finish setup first", isError = true)
            return
        }
        viewModelScope.launch {
            _wakeStatus.value = WakeStatus.Sending
            val r = WolSender.wake(pc.mac, pc.broadcastAddress, pc.port)
            if (r is WolSender.Result.Failed) {
                _wakeStatus.value = WakeStatus.Message(r.reason, isError = true)
                return@launch
            }
            if (pc.ip.isBlank()) {
                _wakeStatus.value = WakeStatus.Message(
                    "Packet sent. Add the PC's IP to auto-verify it woke.",
                )
                return@launch
            }
            _wakeStatus.value = WakeStatus.Verifying
            val awake = PcReachability.waitUntilAwake(pc.ip, attempts = 20, intervalMs = 2000)
            _wakeStatus.value = if (awake) {
                WakeStatus.Awake(pc.name.ifBlank { "Your PC" })
            } else {
                WakeStatus.Message(
                    "No response after 40s. Check BIOS WoL + Fast Startup.",
                    isError = true,
                )
            }
        }
    }

    fun clearStatus() { _wakeStatus.value = WakeStatus.Idle }

    // ---- Settings ----------------------------------------------------------

    fun savePc(pc: PcDevice) = viewModelScope.launch { repo.savePc(pc) }

    fun saveHomeSsid(ssid: String) = viewModelScope.launch { repo.saveHomeSsid(ssid) }

    fun completeSetup() = viewModelScope.launch { repo.setSetupComplete(true) }

    fun setAutoWake(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoWake(enabled) }
        val ctx = getApplication<Application>()
        if (enabled) AutoWakeService.start(ctx) else AutoWakeService.stop(ctx)
    }

    /** Called on launch so the service resumes if it was on but the process died. */
    fun ensureServiceMatchesSetting(enabled: Boolean) {
        if (enabled) AutoWakeService.start(getApplication())
    }
}
