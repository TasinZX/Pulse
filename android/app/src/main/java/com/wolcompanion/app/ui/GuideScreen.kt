package com.wolcompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wolcompanion.app.ui.components.PulseCard
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.PurpleDeep
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary

private data class Step(val title: String, val body: String)

private val steps = listOf(
    Step(
        "1 · Install the Pulse Agent on your PC",
        "In the pc-agent folder, run  Install-PulseAgent.ps1  once (right-click → Run with PowerShell). It starts a tiny background beacon at logon so this app can auto-find your PC — no typing MAC or IP. Then just tap your PC on the Find screen.",
    ),
    Step(
        "2 · Enable WoL in BIOS/UEFI",
        "Reboot into BIOS (usually Del/F2). Look under Power / Advanced for “Wake on LAN”, “Power On By PCI-E”, or “ErP” — enable WoL and, if present, disable ErP/Deep Sleep.",
    ),
    Step(
        "3 · Enable it on the network adapter",
        "Device Manager → Network adapters → your Ethernet adapter → Properties → Power Management: check “Allow this device to wake the computer” and “Only allow a magic packet to wake”. Advanced tab: “Wake on Magic Packet” = Enabled. (pc-setup\\Setup-WoL.ps1 can do this for you.)",
    ),
    Step(
        "4 · Turn off Fast Startup",
        "Control Panel → Power Options → “Choose what the power buttons do” → uncheck “Turn on fast startup”. Fast Startup fully powers down the NIC and is the #1 cause of WoL silently failing.",
    ),
    Step(
        "5 · Use Ethernet if you can",
        "Most Wi-Fi cards can’t wake from a full shutdown. A wired connection makes Wake-on-LAN dramatically more reliable.",
    ),
    Step(
        "6 · Test from OFF",
        "Fully shut the PC down (not Restart), then use Test Wake. If it comes up, you’re done — enable Automatic mode.",
    ),
)

@Composable
fun GuideScreen(onBack: () -> Unit) {
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
            Text("Setup guide", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "One-time PC prerequisites for Wake-on-LAN.",
            color = TextSecondary, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.height(16.dp))

        steps.forEachIndexed { i, step ->
            PulseCard {
                Text(step.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(step.body, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}
