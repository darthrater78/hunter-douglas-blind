package com.scrivtech.powerview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.ActionIcon
import com.scrivtech.powerview.data.Shade
import kotlin.math.roundToInt

/**
 * Builds or edits one [com.scrivtech.powerview.data.ShadeAction].
 *
 * Every field is opt-in, and that is the design rather than an oversight: a
 * null field in a command means "leave this rail where it is", which is a real
 * instruction to the shade. A switch per field is the only honest way to let
 * the user say "close the primary and leave the tilt alone" — a slider on its
 * own cannot distinguish that from "set the tilt to 0".
 */
@Composable
internal fun ActionEditorScreen(
    draft: ActionDraft,
    shades: List<Shade>,
    canDelete: Boolean,
    onLabelChange: (String) -> Unit,
    onIconChange: (ActionIcon) -> Unit,
    onFieldEnabled: (macAddress: String, field: DraftField, enabled: Boolean) -> Unit,
    onFieldPercent: (macAddress: String, field: DraftField, percent: Double) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shadesByMac = shades.associateBy { it.macAddress }
    val validationError = draft.validationError()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.label,
            onValueChange = onLabelChange,
            label = { Text("Action name") },
            placeholder = { Text("Morning") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            Text("Icon", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (icon in ActionIcon.entries) {
                    IconChoice(
                        icon = icon,
                        selected = icon == draft.icon,
                        onClick = { onIconChange(icon) },
                    )
                }
            }
        }

        Text("Shades", style = MaterialTheme.typography.titleSmall)

        for (command in draft.commands) {
            val shade = shadesByMac[command.macAddress]
            CommandDraftCard(
                command = command,
                shade = shade,
                onFieldEnabled = { field, enabled -> onFieldEnabled(command.macAddress, field, enabled) },
                onFieldPercent = { field, percent -> onFieldPercent(command.macAddress, field, percent) },
            )
        }

        if (validationError != null) {
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSave,
            enabled = validationError == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save action")
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }

        if (canDelete) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete action")
            }
        }
    }
}

@Composable
private fun IconChoice(icon: ActionIcon, selected: Boolean, onClick: () -> Unit) {
    // Text rather than glyphs: the project pulls in no icon dependency, and a
    // named choice is unambiguous where a guessed pictogram is not.
    val label = icon.name.lowercase().replaceFirstChar { it.uppercase() }
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun CommandDraftCard(
    command: CommandDraft,
    shade: Shade?,
    onFieldEnabled: (DraftField, Boolean) -> Unit,
    onFieldPercent: (DraftField, Double) -> Unit,
) {
    val capability = shade?.capabilities?.capability

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = shade?.label ?: command.macAddress,
                style = MaterialTheme.typography.titleMedium,
            )

            if (shade == null) {
                // The action targets a shade that has since been forgotten.
                // Kept rather than dropped silently: deleting part of an action
                // because a shade was renamed away would be a nasty surprise.
                Text(
                    "This shade is no longer set up. Its part of the action is kept, but " +
                        "it cannot run until the shade is added again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Unknown capability (shade forgotten, or never decoded) offers the
            // primary rail only — the one field every shade type in the table has.
            if (capability == null || capability.hasPrimaryRail) {
                FieldRow(
                    label = "Primary",
                    field = DraftField.PRIMARY,
                    draft = command.primary,
                    onEnabled = onFieldEnabled,
                    onPercent = onFieldPercent,
                )
            }
            if (capability?.hasSecondaryRail == true) {
                FieldRow(
                    label = "Secondary",
                    field = DraftField.SECONDARY,
                    draft = command.secondary,
                    onEnabled = onFieldEnabled,
                    onPercent = onFieldPercent,
                )
            }
            if (capability?.hasTilt == true) {
                FieldRow(
                    label = "Tilt",
                    field = DraftField.TILT,
                    draft = command.tilt,
                    onEnabled = onFieldEnabled,
                    onPercent = onFieldPercent,
                )
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    field: DraftField,
    draft: FieldDraft,
    onEnabled: (DraftField, Boolean) -> Unit,
    onPercent: (DraftField, Double) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (draft.enabled) {
                    "$label ${draft.percent.roundToInt()}%"
                } else {
                    "$label — leave as is"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = draft.enabled,
                onCheckedChange = { onEnabled(field, it) },
            )
        }
        Slider(
            value = draft.percent.toFloat(),
            onValueChange = { onPercent(field, it.toDouble()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
