package com.wolcompanion.app.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Rocket
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wolcompanion.app.data.AutoSleepConfig
import com.wolcompanion.app.data.Profile
import com.wolcompanion.app.data.Schedule
import com.wolcompanion.app.ui.components.PulseCard
import com.wolcompanion.app.ui.components.SectionLabel
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.Surface2
import com.wolcompanion.app.ui.theme.TextMuted
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary

private val ACTIONS = listOf(
    "wake" to "Wake",
    "wake_profile" to "Wake + profile",
    "sleep" to "Sleep",
    "lock" to "Lock",
    "reboot" to "Reboot",
    "shutdown" to "Shut down",
)
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun AutomationScreen(vm: AutomationViewModel = viewModel(), onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.clearMessage() }
    }

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }

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
            Text("Automation", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Hands-free control of your PC, all in one place.",
            color = TextSecondary, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.height(16.dp))

        AutoSleepCard(state.autoSleep, onChange = vm::setAutoSleep)
        Spacer(Modifier.height(16.dp))

        ProfilesCard(
            profiles = state.profiles,
            onRun = vm::runProfileNow,
            onWake = vm::wakeWithProfile,
            onEdit = { editingProfile = it },
            onNew = { editingProfile = Profile(id = vm.newId(), name = "") },
            onDelete = vm::deleteProfile,
        )
        Spacer(Modifier.height(16.dp))

        SchedulesCard(
            schedules = state.schedules,
            onToggle = vm::toggleSchedule,
            onEdit = { editingSchedule = it },
            onNew = { editingSchedule = Schedule(id = vm.newId(), name = "", timeMinutes = 8 * 60 + 45) },
            onDelete = vm::deleteSchedule,
        )
        Spacer(Modifier.height(16.dp))

        ClipboardCard(
            autoPush = state.clipboard.autoPush,
            onPush = vm::pushClipboard,
            onPull = vm::pullClipboard,
            onAutoPush = vm::setClipboardAutoPush,
        )
        Spacer(Modifier.height(24.dp))
    }

    editingProfile?.let { p ->
        ProfileEditorDialog(
            initial = p,
            onDismiss = { editingProfile = null },
            onSave = { vm.saveProfile(it); editingProfile = null },
        )
    }
    editingSchedule?.let { s ->
        ScheduleEditorDialog(
            initial = s,
            profiles = state.profiles,
            onDismiss = { editingSchedule = null },
            onSave = { vm.saveSchedule(it); editingSchedule = null },
        )
    }
}

// ---- Cards -----------------------------------------------------------------

@Composable
private fun FeatureHeader(icon: ImageVector, title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).background(Purple.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Purple, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun AutoSleepCard(cfg: AutoSleepConfig, onChange: (AutoSleepConfig) -> Unit) {
    PulseCard {
        FeatureHeader(Icons.Rounded.Bedtime, "Auto-sleep on leave") {
            Switch(
                checked = cfg.enabled,
                onCheckedChange = { onChange(cfg.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Purple),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "When you leave home, ${cfg.action} the PC after ${cfg.graceMinutes} min — unless it's in use or you come back.",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        if (cfg.enabled) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Action")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sleep" to "Sleep", "lock" to "Lock").forEach { (v, label) ->
                    ChoiceChip(label, cfg.action == v) { onChange(cfg.copy(action = v)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Grace period")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 15).forEach { m ->
                    ChoiceChip("$m min", cfg.graceMinutes == m) { onChange(cfg.copy(graceMinutes = m)) }
                }
            }
        }
    }
}

@Composable
private fun ProfilesCard(
    profiles: List<Profile>,
    onRun: (Profile) -> Unit,
    onWake: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
) {
    PulseCard {
        FeatureHeader(Icons.Rounded.Rocket, "Launch profiles") {
            IconButton(onClick = onNew) { Icon(Icons.Rounded.Add, "New profile", tint = Purple) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Wake the PC and open a set of apps in one go.",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        if (profiles.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("No profiles yet — tap + to create one.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        profiles.forEach { p ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onEdit(p) }) {
                    Text(p.name.ifBlank { "Untitled" }, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text("${p.entries.size} app${if (p.entries.size == 1) "" else "s"}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { onWake(p) }) { Icon(Icons.Rounded.PlayArrow, "Wake with profile", tint = Purple) }
                IconButton(onClick = { onDelete(p.id) }) { Icon(Icons.Rounded.Delete, "Delete", tint = TextMuted) }
            }
        }
    }
}

@Composable
private fun SchedulesCard(
    schedules: List<Schedule>,
    onToggle: (Schedule, Boolean) -> Unit,
    onEdit: (Schedule) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
) {
    PulseCard {
        FeatureHeader(Icons.Rounded.Schedule, "Scheduled routines") {
            IconButton(onClick = onNew) { Icon(Icons.Rounded.Add, "New routine", tint = Purple) }
        }
        Spacer(Modifier.height(4.dp))
        Text("Run actions at set times — e.g. wake weekdays at 8:45.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (schedules.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("No routines yet — tap + to add one.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        schedules.forEach { s ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onEdit(s) }) {
                    Text(
                        s.name.ifBlank { ACTIONS.firstOrNull { it.first == s.action }?.second ?: "Routine" },
                        color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "%02d:%02d · %s".format(s.hour, s.minute, daysSummary(s.days)),
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { onToggle(s, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Purple),
                )
                IconButton(onClick = { onDelete(s.id) }) { Icon(Icons.Rounded.Delete, "Delete", tint = TextMuted) }
            }
        }
    }
}

@Composable
private fun ClipboardCard(
    autoPush: Boolean,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onAutoPush: (Boolean) -> Unit,
) {
    PulseCard {
        FeatureHeader(Icons.Rounded.ContentPaste, "Clipboard sync")
        Spacer(Modifier.height(4.dp))
        Text("Move text between phone and PC.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Send to PC", false, onClick = onPush)
            ChoiceChip("Get from PC", false, onClick = onPull)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-send on copy", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text("While Pulse is open (Android limits background clipboard).", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = autoPush, onCheckedChange = onAutoPush, colors = SwitchDefaults.colors(checkedTrackColor = Purple))
        }
    }
}

// ---- Editors ---------------------------------------------------------------

@Composable
private fun ProfileEditorDialog(initial: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var entriesText by remember { mutableStateOf(initial.entries.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        confirmButton = {
            TextButton(onClick = {
                val entries = entriesText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                onSave(initial.copy(name = name.trim(), entries = entries))
            }) { Text("Save", color = Purple) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        title = { Text("Launch profile", color = TextPrimary) },
        text = {
            Column {
                DialogField("Name", name, { name = it }, "e.g. Work")
                Spacer(Modifier.height(10.dp))
                DialogField(
                    "Apps / URLs (one per line)", entriesText, { entriesText = it },
                    "C:\\...\\Code.exe\nhttps://mail.google.com\nspotify", singleLine = false,
                )
            }
        },
    )
}

@Composable
private fun ScheduleEditorDialog(
    initial: Schedule,
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var timeMinutes by remember { mutableStateOf(initial.timeMinutes) }
    var days by remember { mutableStateOf(initial.days) }
    var action by remember { mutableStateOf(initial.action) }
    var profileId by remember { mutableStateOf(initial.profileId) }
    var idleMin by remember { mutableStateOf(initial.requireIdleMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(), timeMinutes = timeMinutes, days = days,
                        action = action, profileId = profileId.takeIf { action == "wake_profile" },
                        requireIdleMinutes = if (action in listOf("sleep", "lock", "shutdown")) idleMin else 0,
                        enabled = true,
                    )
                )
            }) { Text("Save", color = Purple) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        title = { Text("Scheduled routine", color = TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DialogField("Name", name, { name = it }, "e.g. Morning wake")
                Spacer(Modifier.height(12.dp))
                SectionLabel("Time")
                Spacer(Modifier.height(6.dp))
                ChoiceChip("%02d:%02d".format(timeMinutes / 60, timeMinutes % 60), true) {
                    TimePickerDialog(
                        context, { _, h, m -> timeMinutes = h * 60 + m },
                        timeMinutes / 60, timeMinutes % 60, true,
                    ).show()
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel("Days")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val d = i + 1
                        ChoiceChip(label, days.contains(d)) {
                            days = if (days.contains(d)) days - d else days + d
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel("Action")
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ACTIONS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (v, label) -> ChoiceChip(label, action == v) { action = v } }
                        }
                    }
                }
                if (action == "wake_profile" && profiles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Profile")
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.forEach { p ->
                            ChoiceChip(p.name.ifBlank { "Untitled" }, profileId == p.id) { profileId = p.id }
                        }
                    }
                }
                if (action in listOf("sleep", "lock", "shutdown")) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Only if idle")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 15, 30, 60).forEach { m ->
                            ChoiceChip(if (m == 0) "Off" else "$m min", idleMin == m) { idleMin = m }
                        }
                    }
                }
            }
        },
    )
}

// ---- Shared bits -----------------------------------------------------------

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Purple,
            selectedLabelColor = TextPrimary,
            containerColor = Surface2,
            labelColor = TextSecondary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextMuted) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = Purple,
            cursorColor = Purple,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun daysSummary(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    days.isEmpty() -> "Never"
    else -> days.sorted().joinToString("") { DAY_LABELS[it - 1] }
}
