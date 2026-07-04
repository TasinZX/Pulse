package com.wolcompanion.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
}

@Composable
fun PulseApp(
    vm: WolViewModel = viewModel(),
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
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
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
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
                canEnableAuto = settings.homeSsid.isNotBlank() && settings.pc.isConfigured && hasLocationPermission,
                hasLocationPermission = hasLocationPermission,
                onToggleAuto = { vm.setAutoWake(it) },
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
