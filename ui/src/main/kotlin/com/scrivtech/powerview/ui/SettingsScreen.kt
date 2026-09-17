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
import com.scrivtech.powerview.data.ThemeMode

/**
 * App settings. Just the colour scheme so far.
 *
 * The options carry a line of explanation each rather than only a name,
 * because "Dark" and "Black (OLED)" are otherwise indistinguishable until you
 * pick one and look.
 */
@Composable
public fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
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
