package com.scrivtech.powerview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.ShadeAction
import com.scrivtech.powerview.data.SweepInterval
import com.scrivtech.powerview.data.ThemeMode

/**
 * App settings: the colour scheme, and which action the Quick Settings tile
 * runs.
 *
 * The theme options carry a line of explanation each rather than only a name,
 * because "Dark" and "Black (OLED)" are otherwise indistinguishable until you
 * pick one and look.
 *
 * The tile is configured here rather than on itself because a tile is one
 * button with nowhere to put a picker — unlike a widget, which gets a
 * configuration activity when it is placed.
 */
@Composable
public fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    actions: List<ShadeAction>,
    tileActionId: String?,
    onTileActionChange: (String?) -> Unit,
    sweepInterval: SweepInterval,
    onSweepIntervalChange: (SweepInterval) -> Unit,
    onSweepNow: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "appearance-header") {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
        }

        item(key = "theme-options") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    for (mode in ThemeMode.entries) {
                        SettingOption(
                            label = themeModeLabel(mode),
                            description = themeModeDescription(mode),
                            selected = mode == themeMode,
                            onSelect = { onThemeModeChange(mode) },
                        )
                    }
                }
            }
        }

        item(key = "battery-header") {
            Text(
                "Battery checks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item(key = "battery-explanation") {
            Text(
                SWEEP_INTERVAL_EXPLANATION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "sweep-options") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    for (interval in SweepInterval.entries) {
                        SettingOption(
                            label = sweepIntervalLabel(interval),
                            description = sweepIntervalDescription(interval),
                            selected = interval == sweepInterval,
                            onSelect = { onSweepIntervalChange(interval) },
                        )
                    }
                }
            }
        }

        item(key = "sweep-now") {
            // The escape hatch that makes Off a real choice rather than a way
            // to never see a reading again.
            Button(onClick = onSweepNow) { Text("Check now") }
        }

        item(key = "notifications-header") {
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item(key = "notifications-toggle") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text("Low battery alerts", style = MaterialTheme.typography.bodyLarge)
                        // Sweeps keep running and the widget keeps updating
                        // either way; this only silences the notification
                        // itself. Turning sweeps off in "Battery checks"
                        // above is the bigger switch.
                        Text(
                            "A notification when a sweep finds a low battery. Readings still " +
                                "happen and still show in the app with this off.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange)
                }
            }
        }

        item(key = "notifications-system-settings") {
            // Sound, vibration and importance for the one channel this app
            // has live in the system settings, not here.
            TextButton(onClick = onOpenSystemNotificationSettings) {
                Text("Open system notification settings")
            }
        }

        item(key = "tile-header") {
            Text(
                "Quick Settings tile",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item(key = "tile-options") {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (actions.isEmpty()) {
                    Text(
                        "No saved actions yet. The tile runs one action, so there " +
                            "has to be one to point at.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Column(modifier = Modifier.selectableGroup()) {
                        // "None" first and always present: turning the tile
                        // off has to be as reachable as turning it on.
                        SettingOption(
                            label = "None",
                            description = "The tile opens the app instead of running anything.",
                            selected = tileActionId == null,
                            onSelect = { onTileActionChange(null) },
                        )
                        for (action in actions) {
                            SettingOption(
                                label = action.label,
                                description = tileActionDescription(action),
                                selected = action.id == tileActionId,
                                onSelect = { onTileActionChange(action.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One radio row: the shape every setting on this screen uses. */
@Composable
private fun SettingOption(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


