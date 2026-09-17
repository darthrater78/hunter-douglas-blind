package com.scrivtech.powerview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.ScanState
import com.scrivtech.powerview.data.Shade

/**
 * Build order step 7's shade list: the app's home screen, as opposed to
 * [DebugScanScreen], which stays available for hardware work.
 *
 * Shades are grouped into rooms, and — before any room — into set up versus
 * merely discovered. That split is the one the user acts on: a discovered
 * shade is an invitation to name it, and naming it is also the moment its
 * `homeId` gets persisted, without which it can never be commanded (see
 * [ShadeListViewModel.saveShade]).
 */
@Composable
public fun ShadeListScreen(
    shades: List<Shade>,
    savedMacAddresses: Set<String>,
    scanState: ScanState,
    onRetryScan: () -> Unit,
    onOpenShade: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = shades.filter { it.macAddress in savedMacAddresses }
    val discovered = shades.filterNot { it.macAddress in savedMacAddresses }
    val rooms = groupIntoRooms(saved)

    if (shades.isEmpty()) {
        EmptyShadeList(scanState = scanState, onRetryScan = onRetryScan, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        val scanFailure = scanState as? ScanState.Failed
        if (scanFailure != null) {
            item(key = "scan-problem") {
                ScanProblemCard(scanState = scanFailure, onRetryScan = onRetryScan)
            }
        }

        for ((room, roomShades) in rooms) {
            item(key = "room-$room") { SectionHeader(room) }
            items(roomShades, key = { it.macAddress }) { shade ->
                ShadeRow(shade = shade, isSetUp = true, onClick = { onOpenShade(shade.macAddress) })
            }
        }

        if (discovered.isNotEmpty()) {
            item(key = "discovered-header") {
                Column {
                    SectionHeader("Found nearby")
                    Text(
                        text = "Not set up yet. Open one to give it a name — that also records " +
                            "the home it belongs to, which is what lets it be controlled later.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            items(discovered, key = { it.macAddress }) { shade ->
                ShadeRow(shade = shade, isSetUp = false, onClick = { onOpenShade(shade.macAddress) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ShadeRow(shade: Shade, isSetUp: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // An un-set-up shade has no label of its own: ShadeRepository
                // seeds `label` with the MAC, which is the most identifying
                // thing available before the user names it.
                Text(
                    text = shade.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = batterySummary(shade),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Text(text = positionSummary(shade), style = MaterialTheme.typography.bodyMedium)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = lastSeenText(shade), style = MaterialTheme.typography.bodySmall)
                if (isSetUp) {
                    Text(
                        text = shade.macAddress,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanProblemCard(scanState: ScanState.Failed, onRetryScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Not scanning", style = MaterialTheme.typography.titleMedium)
            // Saved shades are still listed above/below this card — they come
            // from storage, not from the scan — but their positions will be
            // stale, so the failure is worth showing even when the list is not
            // empty.
            Text(
                text = scanState.reason.message ?: scanState.reason::class.simpleName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetryScan) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyShadeList(scanState: ScanState, onRetryScan: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (title, detail) = when (scanState) {
            ScanState.Scanning ->
                "Looking for shades" to
                    "Shades advertise continuously, so one in range normally appears " +
                    "within a few seconds. Move closer to a shade if nothing shows up."
            ScanState.Stopped ->
                "Not scanning" to "Nothing is listening for advertisements right now."
            is ScanState.Failed ->
                "Can't scan" to (scanState.reason.message ?: "The Bluetooth scan could not start.")
        }

        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (scanState !is ScanState.Scanning) {
            TextButton(onClick = onRetryScan, modifier = Modifier.padding(top = 8.dp)) {
                Text("Retry scan")
            }
        }
    }
}
