package com.wolcompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wolcompanion.app.data.PcDevice
import com.wolcompanion.app.ui.components.PrimaryButton
import com.wolcompanion.app.ui.components.PulseCard
import com.wolcompanion.app.ui.components.SectionLabel
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary

@Composable
fun SetupScreen(
    initialPc: PcDevice,
    initialSsid: String,
    detectedSsid: String?,
    onCaptureSsid: () -> String?,
    onSave: (PcDevice, String) -> Unit,
    onOpenGuide: () -> Unit,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf(initialPc.name) }
    var mac by remember { mutableStateOf(initialPc.mac) }
    var broadcast by remember { mutableStateOf(initialPc.broadcastAddress.ifBlank { "255.255.255.255" }) }
    var ip by remember { mutableStateOf(initialPc.ip) }
    var ssid by remember { mutableStateOf(initialSsid.ifBlank { detectedSsid ?: "" }) }

    val macValid = com.wolcompanion.app.core.wol.WolSender.parseMac(mac) != null

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("Set up your PC", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Run the PC helper script once, then copy the values it prints here.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
        )
        TextButton(onClick = onOpenGuide) { Text("Open setup guide", color = Purple) }

        Spacer(Modifier.height(12.dp))
        PulseCard {
            SectionLabel("PC details")
            Spacer(Modifier.height(12.dp))
            Field("PC name", name, { name = it }, placeholder = "e.g. Battlestation")
            Field("MAC address", mac, { mac = it }, placeholder = "F4:B5:20:59:73:46", error = mac.isNotBlank() && !macValid)
            Field("Subnet broadcast", broadcast, { broadcast = it }, placeholder = "192.168.0.255")
            Field("PC IP (for wake verification)", ip, { ip = it }, placeholder = "192.168.0.241", keyboard = KeyboardType.Number, last = true)
        }

        Spacer(Modifier.height(16.dp))
        PulseCard {
            SectionLabel("Automatic mode")
            Spacer(Modifier.height(8.dp))
            Text(
                "Which Wi-Fi means you're home? Connect to it, then capture.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Field("Home Wi-Fi (SSID)", ssid, { ssid = it }, placeholder = "MyHomeWiFi")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onCaptureSsid()?.let { ssid = it } }) {
                    Icon(Icons.Rounded.Wifi, null, tint = Purple)
                    Text("  Use current network", color = Purple)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Save & continue",
            onClick = {
                onSave(
                    PcDevice(name.trim(), mac.trim(), broadcast.trim(), ip.trim(), initialPc.port),
                    ssid.trim(),
                )
                onDone()
            },
            enabled = macValid && broadcast.isNotBlank(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    error: Boolean = false,
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextSecondary) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = Purple,
            cursorColor = Purple,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 12.dp),
    )
}
