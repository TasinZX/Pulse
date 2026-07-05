package com.wolcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.automationStore by preferencesDataStore(name = "pulse_automation")

/**
 * Single source of truth for the Automation hub. Stores the whole [AutomationState]
 * as one JSON string so adding new automation kinds never needs a migration.
 */
class AutomationRepository(private val context: Context) {

    private val stateKey = stringPreferencesKey("state")

    val state: Flow<AutomationState> =
        context.automationStore.data.map { AutomationState.fromJson(it[stateKey]) }

    suspend fun save(state: AutomationState) {
        context.automationStore.edit { it[stateKey] = state.toJson() }
    }

    // Convenience mutators ----------------------------------------------------

    suspend fun update(transform: (AutomationState) -> AutomationState) {
        context.automationStore.edit { prefs ->
            val current = AutomationState.fromJson(prefs[stateKey])
            prefs[stateKey] = transform(current).toJson()
        }
    }
}
