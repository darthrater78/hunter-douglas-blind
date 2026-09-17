package com.scrivtech.powerview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.ActionResult
import com.scrivtech.powerview.data.ShadeAction

/**
 * The saved actions list. A Gen 3 shade has no on-shade scenes, so a
 * [ShadeAction] — a list of per-shade positions — *is* the scene (spec §3.1),
 * and this is where they are made.
 *
 * Every home-screen surface still to be built (widgets, the Quick Settings
 * tile, shortcuts) references an action by id, so what is edited here is what
 * those will run, with no reconfiguration when an action is renamed or
 * retargeted.
 */
@Composable
public fun ActionsScreen(
    actions: List<ShadeAction>,
    runs: ActionRunUiState,
    labelForMacAddress: (String) -> String,
    canCreate: Boolean,
    onRun: (ShadeAction) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) {
        EmptyActions(canCreate = canCreate, onCreate = onCreate, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(actions, key = { it.id }) { action ->
            ActionRow(
                action = action,
                running = action.id in runs.inFlight,
                result = runs.results[action.id],
                labelForMacAddress = labelForMacAddress,
                onRun = { onRun(action) },
                onEdit = { onEdit(action.id) },
                onDelete = { onDelete(action.id) },
            )
        }

        item(key = "new-action") {
            Button(
                onClick = onCreate,
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("New action")
            }
        }
    }
}

@Composable
private fun ActionRow(
    action: ShadeAction,
    running: Boolean,
    result: ActionResult?,
    labelForMacAddress: (String) -> String,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(action.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${action.commands.size} shade(s) · ${action.icon.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onRun, enabled = !running) {
                    Text(if (running) "Running…" else "Run")
                }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            if (result != null && !running) {
                Text(
                    text = actionResultText(result, labelForMacAddress),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result is ActionResult.Success) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyActions(canCreate: Boolean, onCreate: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No actions yet", style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (canCreate) {
                "An action sets one or more shades to chosen positions. Widgets and the " +
                    "Quick Settings tile will run these, so it is worth building the ones " +
                    "you use daily."
            } else {
                "Set up at least one shade first — an action needs something to command."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (canCreate) {
            Button(onClick = onCreate, modifier = Modifier.padding(top = 16.dp)) {
                Text("New action")
            }
        }
    }
}
