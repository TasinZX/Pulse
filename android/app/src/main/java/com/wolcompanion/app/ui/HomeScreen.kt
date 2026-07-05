package com.wolcompanion.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wolcompanion.app.core.net.WifiState
import com.wolcompanion.app.data.AppSettings
import com.wolcompanion.app.ui.components.PulseCard
import com.wolcompanion.app.ui.theme.ErrorRed
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.PurpleBright
import com.wolcompanion.app.ui.theme.PurpleDeep
import com.wolcompanion.app.ui.theme.PurpleGlow
import com.wolcompanion.app.ui.theme.Stroke
import com.wolcompanion.app.ui.theme.SuccessGreen
import com.wolcompanion.app.ui.theme.Surface2
import com.wolcompanion.app.ui.theme.TextMuted
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary
import com.wolcompanion.app.ui.theme.WarnAmber

@Composable
fun HomeScreen(
    settings: AppSettings,
    wifi: WifiState,
    wakeStatus: WakeStatus,
    onWake: () -> Unit,
    onRemoteDesktop: () -> Unit,
    onPowerOff: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val onHome = wifi.connected && wifi.ssid.equals(settings.homeSsid, ignoreCase = true)
    var showPowerConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pulse", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text(
                    settings.pc.name.ifBlank { "No PC configured" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.pc.ip.isNotBlank()) {
                IconButton(onClick = { showPowerConfirm = true }) {
                    Icon(Icons.Rounded.PowerSettingsNew, "Power off PC", tint = TextSecondary)
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, "Settings", tint = TextSecondary)
            }
        }

        if (showPowerConfirm) {
            AlertDialog(
                onDismissRequest = { showPowerConfirm = false },
                confirmButton = {
                    TextButton(onClick = { showPowerConfirm = false; onPowerOff() }) {
                        Text("Shut down", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPowerConfirm = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                title = { Text("Shut down ${settings.pc.name.ifBlank { "PC" }}?", color = TextPrimary) },
                text = {
                    Text(
                        "This safely powers off your PC. Unsaved work may prompt to be saved first.",
                        color = TextSecondary,
                    )
                },
                containerColor = Surface2,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Network status pill
        NetworkPill(wifi = wifi, onHome = onHome, homeSsid = settings.homeSsid)

        Spacer(Modifier.weight(1f))

        // The wake orb
        WakeOrb(status = wakeStatus, onClick = onWake)

        Spacer(Modifier.height(28.dp))

        // Status text under the orb
        StatusCaption(wakeStatus)

        Spacer(Modifier.weight(1f))

        // Remote Desktop — full control of the PC (via RDP)
        RemoteDesktopButton(
            enabled = settings.pc.ip.isNotBlank(),
            onClick = onRemoteDesktop,
        )

        Spacer(Modifier.height(12.dp))

        // Mode summary cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChip(
                modifier = Modifier.weight(1f),
                title = "Automatic",
                active = settings.autoWakeEnabled,
                subtitle = if (settings.autoWakeEnabled) "On • ${settings.homeSsid}" else "Off",
            )
            ModeChip(
                modifier = Modifier.weight(1f),
                title = "Manual",
                active = true,
                subtitle = "Tap to wake",
            )
        }
    }
}

@Composable
private fun WakeOrb(status: WakeStatus, onClick: () -> Unit) {
    val busy = status is WakeStatus.Sending || status is WakeStatus.Verifying
    val transition = rememberInfiniteTransition(label = "orb")
    val glow by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = if (pressed) 0.94f else 1f

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Outer glow ring
        Box(
            Modifier
                .size(260.dp)
                .scale(glow)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(PurpleGlow, androidx.compose.ui.graphics.Color.Transparent))),
        )
        // Core button
        Box(
            Modifier
                .size(180.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(PurpleBright, PurpleDeep)))
                .clickable(interactionSource = interaction, indication = null, enabled = !busy) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = TextPrimary, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
            } else if (status is WakeStatus.Awake) {
                Icon(Icons.Rounded.Check, "Awake", tint = TextPrimary, modifier = Modifier.size(72.dp))
            } else {
                Icon(Icons.Rounded.Bolt, "Wake", tint = TextPrimary, modifier = Modifier.size(72.dp))
            }
        }
    }
}

@Composable
private fun StatusCaption(status: WakeStatus) {
    val (text, color) = when (status) {
        is WakeStatus.Idle -> "Tap to wake your PC" to TextSecondary
        is WakeStatus.Sending -> "Sending wake signal…" to PurpleBright
        is WakeStatus.Sent -> "Magic packet sent → ${status.target}" to SuccessGreen
        is WakeStatus.Verifying -> "Waiting for your PC to come online…" to WarnAmber
        is WakeStatus.Awake -> "${status.name} is awake ✓" to SuccessGreen
        is WakeStatus.Message -> status.text to if (status.isError) ErrorRed else TextSecondary
    }
    AnimatedContent(targetState = text, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "caption") { t ->
        Text(
            t,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NetworkPill(wifi: WifiState, onHome: Boolean, homeSsid: String) {
    val (label, dot) = when {
        onHome -> "Home network • ${wifi.ssid}" to SuccessGreen
        wifi.connected -> "On ${wifi.ssid ?: "Wi-Fi"}" to WarnAmber
        else -> "No Wi-Fi" to TextMuted
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemoteDesktopButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = Surface2,
        border = BorderStroke(1.dp, if (enabled) Purple.copy(alpha = 0.45f) else Stroke),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.DesktopWindows,
                contentDescription = null,
                tint = if (enabled) PurpleBright else TextMuted,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Remote Desktop",
                color = if (enabled) TextPrimary else TextMuted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ModeChip(modifier: Modifier, title: String, active: Boolean, subtitle: String) {
    PulseCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (active) Purple else TextMuted),
            )
            Spacer(Modifier.width(8.dp))
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
