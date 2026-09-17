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
    private val json = Json { ignoreUnknownKeys = true }

    public val actions: Flow<List<ShadeAction>> = context.actionsDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<ShadeAction>>(raw) }.getOrDefault(emptyList())
    }

    public suspend fun upsert(action: ShadeAction) {
        context.actionsDataStore.edit { prefs ->
            val current = currentList(prefs)
            val next = current.filterNot { it.id == action.id } + action
            prefs[key] = json.encodeToString(next)
        }
    }

    public suspend fun remove(actionId: String) {
        context.actionsDataStore.edit { prefs ->
            val current = currentList(prefs)
            prefs[key] = json.encodeToString(current.filterNot { it.id == actionId })
        }
    }

    private fun currentList(prefs: Preferences): List<ShadeAction> {
        val raw = prefs[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ShadeAction>>(raw) }.getOrDefault(emptyList())
    }
}
