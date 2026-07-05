package com.wolcompanion.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.wolcompanion.app.service.ClipboardWatcher
import com.wolcompanion.app.ui.PulseApp as PulseAppUi
import com.wolcompanion.app.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private var hasLocation by mutableStateOf(false)
    private val clipboardWatcher by lazy { ClipboardWatcher(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            PulseTheme {
                PulseAppUi(
                    hasLocationPermission = hasLocation,
                    onRequestLocation = { requestPermissions() },
                    onEnableAutoReliability = { ensureAutoWakeReliability() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clipboardWatcher.start()
    }

    override fun onPause() {
        clipboardWatcher.stop()
        super.onPause()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    /**
     * Called when the user turns on Automatic Mode: ask to be exempted from battery
     * optimization so the OS won't kill the watcher service, and nudge for background
     * location (optional — subnet matching works without it).
     */
    private fun ensureAutoWakeReliability() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
    }
}
