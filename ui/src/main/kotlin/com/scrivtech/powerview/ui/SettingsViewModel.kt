package com.scrivtech.powerview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrivtech.powerview.data.SettingsStore
import com.scrivtech.powerview.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the settings screen, and is also read by the activity itself: the
 * theme has to be known before any content is composed, so this one is hoisted
 * above [PowerViewApp] rather than being owned by the screen that edits it.
 */
public class SettingsViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /**
     * `Eagerly` rather than `WhileSubscribed`: this drives the colour scheme
     * for the whole window, so dropping back to the initial value while
     * nothing is collecting — between a configuration change tearing the
     * composition down and the new one subscribing — would flash the wrong
     * theme at the user.
     *
     * A cold start still shows one frame of [ThemeMode.SYSTEM] before the
     * stored value arrives from disk, because the read is asynchronous and
     * blocking the first frame on it would be the worse trade. The night
     * window background (`values-night/themes.xml`) is what keeps that frame
     * from being bright.
     */
    public val themeMode: StateFlow<ThemeMode> = settingsStore.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    public fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    /**
     * Which action the Quick Settings tile runs, or null for none.
     *
     * `WhileSubscribed` unlike [themeMode]: nothing outside the settings
     * screen reads this, so there is no wrong-value flash to avoid.
     */
    public val tileActionId: StateFlow<String?> = settingsStore.tileActionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), null)

    public fun setTileActionId(actionId: String?) {
        viewModelScope.launch { settingsStore.setTileActionId(actionId) }
    }

    public class Factory(
        private val settingsStore: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(settingsStore) as T
        }
    }
}
