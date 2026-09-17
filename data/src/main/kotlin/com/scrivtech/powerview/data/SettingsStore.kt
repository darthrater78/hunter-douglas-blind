package com.scrivtech.powerview.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * App-wide user preferences. Currently just the colour scheme; this is the
 * place for anything else that is a setting rather than per-shade data.
 *
 * Separate DataStore from [ShadeStore] and [ActionStore] on purpose: a corrupt
 * or cleared settings blob should cost the user their theme choice, not their
 * shade list.
 */
public class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val tileActionIdKey = stringPreferencesKey("tile_action_id")
    private val sweepIntervalKey = stringPreferencesKey("sweep_interval")

    /**
     * The chosen [ThemeMode], defaulting to [ThemeMode.SYSTEM].
     *
     * An unrecognised stored value also reads as [ThemeMode.SYSTEM] rather
     * than throwing: the only way one gets there is a downgrade from a build
     * that knew a mode this one does not, and following the system is the
     * right fallback for a theme nobody can render.
     */
    public val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    public suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
    }

    /**
     * Which saved action the Quick Settings tile runs, or null for none.
     *
     * A tile has one button, so unlike a widget it cannot be configured
     * in place — there is nowhere to put a picker. It is chosen in the app's
     * settings instead, which is why this lives here rather than in
     * `:widget`: `:ui` writes it and `:widget` reads it, and the two are
     * peers that cannot see each other.
     *
     * The id is stored rather than the action itself, so renaming or
     * retargeting an action updates the tile with no reconfiguration — the
     * same contract widgets and shortcuts have.
     */
    public val tileActionId: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[tileActionIdKey]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged()

    public suspend fun setTileActionId(actionId: String?) {
        context.settingsDataStore.edit { prefs ->
            if (actionId.isNullOrBlank()) prefs.remove(tileActionIdKey) else prefs[tileActionIdKey] = actionId
        }
    }

    /**
     * How often the battery sweep runs, defaulting to
     * [SweepInterval.DEFAULT] — the weekly period it had before it was
     * configurable, so an install that never touches this keeps its old
     * behaviour.
     *
     * `distinctUntilChanged` matters more here than for the others: all
     * three settings share one DataStore, so without it changing the theme
     * would re-emit this and reschedule the sweep, restarting its period
     * every time someone toggled dark mode.
     */
    public val sweepInterval: Flow<SweepInterval> = context.settingsDataStore.data.map { prefs ->
        prefs[sweepIntervalKey]?.let { stored ->
            SweepInterval.entries.firstOrNull { it.name == stored }
        } ?: SweepInterval.DEFAULT
    }.distinctUntilChanged()

    public suspend fun setSweepInterval(interval: SweepInterval) {
        context.settingsDataStore.edit { prefs -> prefs[sweepIntervalKey] = interval.name }
    }
}
