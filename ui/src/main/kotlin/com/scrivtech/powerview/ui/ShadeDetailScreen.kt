package com.scrivtech.powerview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.Shade

/**
 * Build order step 7: naming a shade and filing it in a room.
 *
 * Saving is not only cosmetic. It is the point at which the shade's `homeId`
 * and decoded capability get persisted, and `homeId` is what
 * [com.scrivtech.powerview.data.ActionRunner] needs to find a keystream — so
 * for an un-set-up shade this screen is really an adoption step wearing a
 * rename screen's clothes. The copy says so rather than leaving the user to
 * wonder why naming something mattered.
 */
@Composable
public fun ShadeDetailScreen(
    shade: Shade,
    isSetUp: Boolean,
    batteryReading: Boolean,
    batteryMessage: String?,
    onSave: (label: String, room: String?, mainsPowered: Boolean) -> Unit,
    onForget: () -> Unit,
    onReadBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the MAC so switching shades reseeds, but a later advertisement
    // for the same shade does not overwrite what the user is part-way through
    // typing.
    var label by rememberSaveable(shade.macAddress) {
        // An un-set-up shade's label is its MAC (ShadeRepository seeds it that
        // way). Offering that as the starting text just makes the user clear it.
        mutableStateOf(if (isSetUp) shade.label else "")
    }
    var room by rememberSaveable(shade.macAddress) { mutableStateOf(shade.room.orEmpty()) }
    var mainsPowered by rememberSaveable(shade.macAddress) { mutableStateOf(shade.mainsPowered) }
    var confirmForget by rememberSaveable(shade.macAddress) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isSetUp) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Not set up yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Giving this shade a name also records which home it belongs to. " +
                            "That is what later lets it be controlled, so it is worth doing " +
                            "while the shade is in range.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Name") },
            placeholder = { Text(shade.macAddress) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = room,
            onValueChange = { room = it },
            label = { Text("Room") },
            placeholder = { Text("Optional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text("Mains powered", style = MaterialTheme.typography.bodyLarge)
                // Spec §2.5, "Hardwired shades": excluded from the battery
                // sweep and from low-battery alerting (build order step 10).
                Text(
                    "Skip this shade when checking batteries.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = mainsPowered, onCheckedChange = { mainsPowered = it })
        }

        Button(
            onClick = { onSave(label, room, mainsPowered) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSetUp) "Save" else "Add this shade")
        }

        ShadeFactsCard(shade = shade)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Battery: ${batteryText(shade)}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    // Reading costs the shade power, which is why it is on
                    // demand rather than polled (build order step 4).
                    "Reading the battery connects to the shade, which uses a little of its power.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (batteryMessage != null) {
                    Text(
                        text = batteryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                OutlinedButton(
                    onClick = onReadBattery,
                    enabled = !batteryReading,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(if (batteryReading) "Reading…" else "Read battery")
                }
            }
        }

        if (isSetUp) {
            OutlinedButton(
                onClick = { confirmForget = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Forget this shade")
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget ${shade.label}?") },
            text = {
                Text(
                    "Its name, room and battery history are deleted from this app. The " +
                        "shade itself is not changed and will keep appearing under " +
                        "\"Found nearby\" while it is in range.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForget()
                }) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

/** The decoded facts, for when a shade is not behaving and the user needs something to report. */
@Composable
private fun ShadeFactsCard(shade: Shade) {
    val capability = shade.capabilities?.capability

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            Text(shade.macAddress, style = MaterialTheme.typography.bodyMedium)
            Text(positionSummary(shade), style = MaterialTheme.typography.bodyMedium)
            Text(lastSeenText(shade), style = MaterialTheme.typography.bodySmall)

            val capabilityText = when {
                capability == null -> "Not decoded yet"
                shade.capabilities?.isKnownType == false ->
                    "${capability.name} (unrecognised type ${shade.state?.typeId ?: "?"})"
                else -> capability.name
            }
            Text("Type: $capabilityText", style = MaterialTheme.typography.bodySmall)
            Text("Home: ${shade.homeId ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
