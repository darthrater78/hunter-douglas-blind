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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.ShadeAction
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
                        ThemeOption(
                            mode = mode,
                            selected = mode == themeMode,
                            onSelect = { onThemeModeChange(mode) },
                        )
                    }
                }
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
                        TileOption(
                            label = "None",
                            description = "The tile opens the app instead of running anything.",
                            selected = tileActionId == null,
                            onSelect = { onTileActionChange(null) },
                        )
                        for (action in actions) {
                            TileOption(
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

@Composable
private fun TileOption(
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

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable() on the row, with the radio button's own onClick
            // null: this makes the whole row one accessibility target
            // announced as a radio button, instead of a tiny circle beside
            // unrelated text.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(themeModeLabel(mode), style = MaterialTheme.typography.bodyLarge)
            Text(
                themeModeDescription(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
