package com.wolcompanion.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data model for the Automation hub. Everything here is persisted as one JSON blob
 * (see [AutomationRepository]) so new automation kinds can be added without schema
 * migrations. Kept dependency-free (org.json) to match the rest of the app.
 *
 * Action vocabulary sent to the PC agent: wake, wake_profile, sleep, lock, reboot,
 * shutdown, clipboard_push, clipboard_pull.
 */

/** A launch profile — a named set of apps/URLs/commands to run after waking. */
data class Profile(
    val id: String,
    val name: String,
    val icon: String = "rocket",
    val entries: List<String> = emptyList(), // exe paths, URLs, or shell commands
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("icon", icon)
        .put("entries", JSONArray(entries))

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            id = o.optString("id"),
            name = o.optString("name"),
            icon = o.optString("icon", "rocket"),
            entries = o.optJSONArray("entries").toStringList(),
        )
    }
}

/** A scheduled routine — fires at [timeMinutes] on the selected [days]. */
data class Schedule(
    val id: String,
    val name: String,
    val timeMinutes: Int,            // minutes since midnight (local)
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon .. 7=Sun
    val action: String = "wake",
    val profileId: String? = null,   // for action == wake_profile
    val requireIdleMinutes: Int = 0, // 0 = no idle condition (used by sleep/lock)
    val enabled: Boolean = true,
) {
    val hour get() = timeMinutes / 60
    val minute get() = timeMinutes % 60

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("timeMinutes", timeMinutes)
        .put("days", JSONArray(days.toList()))
        .put("action", action).put("profileId", profileId)
        .put("requireIdleMinutes", requireIdleMinutes).put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject) = Schedule(
            id = o.optString("id"),
            name = o.optString("name"),
            timeMinutes = o.optInt("timeMinutes"),
            days = o.optJSONArray("days").toIntList().toSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) },
            action = o.optString("action", "wake"),
            profileId = if (o.isNull("profileId")) null else o.optString("profileId"),
            requireIdleMinutes = o.optInt("requireIdleMinutes"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

/** Leave-home auto-sleep configuration. */
data class AutoSleepConfig(
    val enabled: Boolean = false,
    val graceMinutes: Int = 5,
    val action: String = "sleep",     // sleep | lock
    val skipIfBusyMinutes: Int = 3,   // don't sleep if PC had input in the last N min
)

/** Clipboard-sync configuration. */
data class ClipboardConfig(
    val autoPush: Boolean = false,     // push phone clipboard while app foreground
)

/** Full persisted automation state. */
data class AutomationState(
    val autoSleep: AutoSleepConfig = AutoSleepConfig(),
    val clipboard: ClipboardConfig = ClipboardConfig(),
    val profiles: List<Profile> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("autoSleep", JSONObject()
            .put("enabled", autoSleep.enabled)
            .put("graceMinutes", autoSleep.graceMinutes)
            .put("action", autoSleep.action)
            .put("skipIfBusyMinutes", autoSleep.skipIfBusyMinutes))
        put("clipboard", JSONObject().put("autoPush", clipboard.autoPush))
        put("profiles", JSONArray(profiles.map { it.toJson() }))
        put("schedules", JSONArray(schedules.map { it.toJson() }))
    }.toString()

    companion object {
        fun fromJson(s: String?): AutomationState {
            if (s.isNullOrBlank()) return AutomationState()
            return try {
                val o = JSONObject(s)
                val a = o.optJSONObject("autoSleep") ?: JSONObject()
                val c = o.optJSONObject("clipboard") ?: JSONObject()
                AutomationState(
                    autoSleep = AutoSleepConfig(
                        enabled = a.optBoolean("enabled"),
                        graceMinutes = a.optInt("graceMinutes", 5),
                        action = a.optString("action", "sleep"),
                        skipIfBusyMinutes = a.optInt("skipIfBusyMinutes", 3),
                    ),
                    clipboard = ClipboardConfig(autoPush = c.optBoolean("autoPush")),
                    profiles = o.optJSONArray("profiles").toObjectList().map { Profile.fromJson(it) },
                    schedules = o.optJSONArray("schedules").toObjectList().map { Schedule.fromJson(it) },
                )
            } catch (e: Exception) {
                AutomationState()
            }
        }
    }
}

// ---- small JSON helpers ----------------------------------------------------

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { optInt(it) }
}

private fun JSONArray?.toObjectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}
