package com.wolcompanion.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wolcompanion.app.data.PcDevice
import com.wolcompanion.app.ui.components.PulseCard
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.PurpleBright
import com.wolcompanion.app.ui.theme.PurpleDeep
import com.wolcompanion.app.ui.theme.PurpleGlow
import com.wolcompanion.app.ui.theme.SuccessGreen
import com.wolcompanion.app.ui.theme.TextMuted
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary

@Composable
fun DiscoverScreen(
    discovered: List<PcDevice>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onPair: (PcDevice) -> Unit,
    onManual: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    // Auto-scan when the screen first appears.
    LaunchedEffect(Unit) { onScan() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Find your PC", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make sure the Pulse Agent is running on your PC and both are on the same Wi-Fi. It'll show up below automatically.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
        )

        Spacer(Modifier.height(28.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                discovered.isEmpty() && isScanning -> ScanningState()
                discovered.isEmpty() -> EmptyState()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    discovered.forEach { pc ->
                        PcRow(pc = pc, onClick = { onPair(pc) })
                        Spacer(Modifier.height(12.dp))
                    }
                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Purple, strokeWidth = 2.dp)
                            Text("  Still looking…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Actions
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onScan, enabled = !isScanning) {
                Icon(Icons.Rounded.Refresh, null, tint = Purple)
                Text("  Rescan", color = Purple)
            }
            TextButton(onClick = onManual) { Text("Enter manually", color = TextSecondary) }
        }
        TextButton(onClick = onOpenGuide) { Text("How to install the PC Agent", color = Purple) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PcRow(pc: PcDevice, onClick: () -> Unit) {
    PulseCard(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PurpleBright, PurpleDeep))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Computer, null, tint = TextPrimary) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pc.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(pc.ip.ifBlank { pc.mac }, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))
                Text("  Tap to pair", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ScanningState() {
    val t = rememberInfiniteTransition(label = "scan")
    val pulse by t.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape)
                .background(Brush.radialGradient(listOf(PurpleGlow, Color.Transparent))))
            Icon(Icons.Rounded.Computer, null, tint = PurpleBright, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Scanning your network…", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Computer, null, tint = TextMuted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("No PCs found yet", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Install & start the Pulse Agent on your PC, then tap Rescan.",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
