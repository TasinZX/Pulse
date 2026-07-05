package com.wolcompanion.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val SETUP = "setup"
    const val SETTINGS = "settings"
    const val GUIDE = "guide"
    const val REMOTE = "remote"
    const val AUTOMATION = "automation"
}

@Composable
fun PulseApp(
    vm: WolViewModel = viewModel(),
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onEnableAutoReliability: () -> Unit = {},
) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val wifi by vm.wifi.collectAsStateWithLifecycle()
    val wakeStatus by vm.wakeStatus.collectAsStateWithLifecycle()

    val discovered by vm.discovered.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()

    val start = if (settings.setupComplete) Routes.HOME else Routes.DISCOVER

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.DISCOVER) {
            DiscoverScreen(
                discovered = discovered,
                isScanning = isScanning,
                onScan = { vm.scanForPcs() },
                onPair = { pc ->
                    vm.pair(pc)
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.DISCOVER) { inclusive = true }
                    }
                },
                onManual = { nav.navigate(Routes.SETUP) },
                onOpenGuide = { nav.navigate(Routes.GUIDE) },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                wifi = wifi,
                wakeStatus = wakeStatus,
                onWake = { vm.wakeNow() },
                onRemoteDesktop = { nav.navigate(Routes.REMOTE) },
                onPowerOff = { vm.powerOff() },
                onOpenAutomation = { nav.navigate(Routes.AUTOMATION) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.AUTOMATION) {
            AutomationScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.REMOTE) {
            RemoteDesktopScreen(
                host = settings.pc.ip,
                pcName = settings.pc.name.ifBlank { "PC" },
                onExit = { nav.popBackStack() },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                initialPc = settings.pc,
                initialSsid = settings.homeSsid,
                detectedSsid = vm.currentSsid(),
                onCaptureSsid = { vm.currentSsid() },
                onSave = { pc, ssid ->
                    vm.savePc(pc)
                    vm.saveHomeSsid(ssid)
                    vm.completeSetup()
                },
                onOpenGuide = { nav.navigate(Routes.GUIDE) },
                onDone = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                // Subnet matching needs only a paired PC (with an IP) — no SSID/location required.
                canEnableAuto = settings.pc.isConfigured &&
                    (settings.pc.ip.isNotBlank() || settings.homeSsid.isNotBlank()),
                hasLocationPermission = hasLocationPermission,
                onToggleAuto = { enabled ->
                    vm.setAutoWake(enabled)
                    if (enabled) onEnableAutoReliability()
                },
                onRequestLocation = onRequestLocation,
                onTestWake = { vm.testWake() },
                onEditPc = { nav.navigate(Routes.SETUP) },
                onOpenGuide = { nav.navigate(Routes.GUIDE) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.GUIDE) {
            GuideScreen(onBack = { nav.popBackStack() })
        }
    }
}
