package com.wolcompanion.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.wolcompanion.app.core.net.WifiState
import com.wolcompanion.app.data.AppSettings
import com.wolcompanion.app.data.PcDevice
import com.wolcompanion.app.ui.theme.PulseTheme

/**
 * In-IDE live previews. Open this file in Android Studio and click the "Split"
 * or "Design" view (top-right of the editor) to see the screens render live —
 * no emulator or phone required. Edit a color or string and it refreshes.
 */

private val sampleSettings = AppSettings(
    pc = PcDevice(
        name = "Battlestation",
        mac = "F4:B5:20:59:73:46",
        broadcastAddress = "192.168.0.255",
        ip = "192.168.0.241",
    ),
    homeSsid = "MyHomeWiFi",
    autoWakeEnabled = true,
    setupComplete = true,
)

@Preview(name = "Home — idle", showBackground = true, backgroundColor = 0xFF0B0B0F, heightDp = 780)
@Composable
private fun HomeIdlePreview() {
    PulseTheme {
        HomeScreen(
            settings = sampleSettings,
            wifi = WifiState(connected = true, ssid = "MyHomeWiFi"),
            wakeStatus = WakeStatus.Idle,
            onWake = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Home — awake", showBackground = true, backgroundColor = 0xFF0B0B0F, heightDp = 780)
@Composable
private fun HomeAwakePreview() {
    PulseTheme {
        HomeScreen(
            settings = sampleSettings,
            wifi = WifiState(connected = true, ssid = "MyHomeWiFi"),
            wakeStatus = WakeStatus.Awake("Battlestation"),
            onWake = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Setup", showBackground = true, backgroundColor = 0xFF0B0B0F, heightDp = 900)
@Composable
private fun SetupPreview() {
    PulseTheme {
        SetupScreen(
            initialPc = PcDevice(),
            initialSsid = "",
            detectedSsid = "MyHomeWiFi",
            onCaptureSsid = { "MyHomeWiFi" },
            onSave = { _, _ -> },
            onOpenGuide = {},
            onDone = {},
        )
    }
}

@Preview(name = "Settings", showBackground = true, backgroundColor = 0xFF0B0B0F, heightDp = 900)
@Composable
private fun SettingsPreview() {
    PulseTheme {
        SettingsScreen(
            settings = sampleSettings,
            canEnableAuto = true,
            hasLocationPermission = true,
            onToggleAuto = {},
            onRequestLocation = {},
            onTestWake = {},
            onEditPc = {},
            onOpenGuide = {},
            onBack = {},
        )
    }
}

@Preview(name = "Guide", showBackground = true, backgroundColor = 0xFF0B0B0F, heightDp = 900)
@Composable
private fun GuidePreview() {
    PulseTheme {
        GuideScreen(onBack = {})
    }
}
