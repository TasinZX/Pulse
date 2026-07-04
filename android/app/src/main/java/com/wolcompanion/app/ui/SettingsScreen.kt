package com.wolcompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wolcompanion.app.data.AppSettings
import com.wolcompanion.app.ui.components.PrimaryButton
import com.wolcompanion.app.ui.components.PulseCard
import com.wolcompanion.app.ui.components.SectionLabel
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    settings: AppSettings,
    canEnableAuto: Boolean,
    hasLocationPermission: Boolean,
    onToggleAuto: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onTestWake: () -> Unit,
    onEditPc: () -> Unit,
    onOpenGuide: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Spacer(Modifier.height(16.dp))

        // PC card
        PulseCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Your PC")
                    Spacer(Modifier.height(8.dp))
                    Text(settings.pc.name.ifBlank { "Unnamed PC" }, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(settings.pc.mac.ifBlank { "No MAC set" }, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text("→ ${settings.pc.broadcastAddress}:${settings.pc.port}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onEditPc) { Icon(Icons.Rounded.Edit, "Edit", tint = Purple) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Automatic mode toggle
        PulseCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic mode", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.homeSsid.isBlank()) "Set a home Wi-Fi first"
                        else "Wake when joining “${settings.homeSsid}”",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.autoWakeEnabled,
                    onCheckedChange = { onToggleAuto(it) },
                    enabled = canEnableAuto,
                    colors = SwitchDefaults.colors(checkedTrackColor = Purple),
                )
            }
            if (!hasLocationPermission) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android needs Location permission to read the Wi-Fi name in the background.",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRequestLocation) { Text("Grant location", color = Purple) }
            }
        }

        Spacer(Modifier.height(16.dp))

        PulseCard {
            SectionLabel("Diagnostics")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Test wake", onClick = onTestWake)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sends a magic packet, then checks whether the PC responds on the network.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onOpenGuide) { Text("PC setup guide (BIOS / Fast Startup)", color = Purple) }
        Spacer(Modifier.height(24.dp))
    }
}
