package com.scrivtech.powerview.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.actionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "shade_actions")

/**
 * Persists [ShadeAction]s keyed by their stable [ShadeAction.id]. Widgets,
 * the Quick Settings tile and shortcuts all reference actions by id (spec
 * §3.1), so renaming or retargeting an action here updates every surface
 * pointing at it with no reconfiguration.
 */
public class ActionStore(private val context: Context) {

    private val key = stringPreferencesKey("actions_json")
    private val quarantineKey = stringPreferencesKey("actions_json_unreadable")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every saved action. Empty (not an error) until anything has been saved,
     * and also if the stored blob cannot be parsed — see [edit] for why an
     * unparseable blob is preserved rather than overwritten.
     */
    public val actions: Flow<List<ShadeAction>> = context.actionsDataStore.data.map { prefs ->
        decodeOrNull(prefs[key]) ?: emptyList()
    }

    public suspend fun upsert(action: ShadeAction) {
        edit { current -> current.filterNot { it.id == action.id } + action }
    }

    public suspend fun remove(actionId: String) {
        edit { current -> current.filterNot { it.id == actionId } }
    }

    /**
     * Reads, transforms and writes back the stored list.
     *
     * An unparseable blob is moved aside under [quarantineKey] before the store
     * starts fresh, rather than being silently replaced by the transformed
     * empty list. Losing the action list also breaks every widget and tile
     * pointing at those ids, so this is worse than it looks: the user would
     * have to reconfigure every home-screen surface.
     */
    private suspend fun edit(transform: (List<ShadeAction>) -> List<ShadeAction>) {
        context.actionsDataStore.edit { prefs ->
            val raw = prefs[key]
            val current = decodeOrNull(raw)
            if (current == null && raw != null) {
                prefs[quarantineKey] = raw
            }
            prefs[key] = json.encodeToString(transform(current ?: emptyList()))
        }
    }

    /** Null means "present but unreadable"; an empty list means "nothing stored yet". */
    private fun decodeOrNull(raw: String?): List<ShadeAction>? {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString<List<ShadeAction>>(raw) }.getOrNull()
    }
}
