package com.wolcompanion.app.data

/** A PC the app can wake. Modeled as its own type so multi-PC support drops in later. */
data class PcDevice(
    val name: String = "",
    val mac: String = "",
    val broadcastAddress: String = "255.255.255.255",
    val ip: String = "",          // used for the "is it awake?" probe
    val port: Int = 9,
) {
    val isConfigured: Boolean
        get() = mac.isNotBlank() && broadcastAddress.isNotBlank()
}

/** Full persisted app configuration. */
data class AppSettings(
    val pc: PcDevice = PcDevice(),
    val homeSsid: String = "",
    val autoWakeEnabled: Boolean = false,
    val setupComplete: Boolean = false,
)
