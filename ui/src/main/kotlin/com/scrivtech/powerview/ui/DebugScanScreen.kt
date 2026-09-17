package com.scrivtech.powerview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.BatteryLevel
import com.scrivtech.powerview.data.ScanState
import com.scrivtech.powerview.data.Shade
import com.scrivtech.powerview.data.batteryLevelOf
import java.util.Locale

/**
 * Build order step 2: "Scanner + a raw debug screen listing MAC / RSSI /
 * decoded ShadeState / hex payload. Confirms your offsets against real shades
 * before any writes."
 *
 * The raw payload is the point of this screen, not a nicety. Decoded fields
 * alone only ever show you the parser's own opinion of the bytes; confirming
 * an offset means putting the decode next to the hex it came from. Fields the
 * shade's capability says do not exist are still shown — this is a debug view,
 * and a byte that should be meaningless is exactly the kind of thing worth
 * seeing — but they are labelled so an unused byte is not mistaken for a real
 * position.
 */
@Composable
public fun DebugScanScreen(viewModel: ShadeListViewModel, modifier: Modifier = Modifier) {
    val shades by viewModel.shades.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val batteryReads by viewModel.batteryReads.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        ScanStatusCard(
            scanState = scanState,
            shadeCount = shades.size,
            onRetry = viewModel::retryScan,
        )

        if (shades.isEmpty()) {
            Text(
                text = "No shades decoded yet.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "Advertisements arrive continuously, so a shade in range normally " +
                    "appears within a few seconds. If nothing shows up: check the status " +
                    "above, confirm Bluetooth is on, and move closer to a shade.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(shades, key = { it.macAddress }) { shade ->
                    ShadeDebugRow(
                        shade = shade,
                        batteryReading = shade.macAddress in batteryReads.inFlight,
                        batteryMessage = batteryReads.messages[shade.macAddress],
                        onReadBattery = { viewModel.readBattery(shade.macAddress) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanStatusCard(scanState: ScanState, shadeCount: Int, onRetry: () -> Unit) {
    val colors = when (scanState) {
        is ScanState.Failed -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> CardDefaults.cardColors()
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (scanState) {
                ScanState.Scanning -> {
                    Text("Scanning", style = MaterialTheme.typography.titleMedium)
                    Text("$shadeCount shade(s) decoded so far.")
                }
                ScanState.Stopped -> {
                    Text("Scan stopped", style = MaterialTheme.typography.titleMedium)
                    Text("Nothing is listening for advertisements right now.")
                }
                is ScanState.Failed -> {
                    Text("Scan failed", style = MaterialTheme.typography.titleMedium)
                    // SecurityException here means BLUETOOTH_SCAN was not granted;
                    // IllegalStateException means the adapter is off or missing.
                    // See ShadeScanner.scan.
                    Text(scanState.reason.message ?: scanState.reason::class.simpleName.orEmpty())
                }
            }

            if (scanState !is ScanState.Scanning) {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry scan")
                }
            }
        }
    }
}

@Composable
private fun ShadeDebugRow(
    shade: Shade,
    batteryReading: Boolean,
    batteryMessage: String?,
    onReadBattery: () -> Unit,
) {
    val capability = shade.capabilities?.capability

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(shade.macAddress, style = MaterialTheme.typography.titleMedium)

            Text(
                "homeId: ${shade.homeId ?: "?"}  " +
                    "typeId: ${shade.state?.typeId ?: "?"}  " +
                    "rssi: ${shade.lastRssi?.let { "$it dBm" } ?: "-"}",
            )

            Text("primary: ${percent(shade.state?.primaryPercent)}${unsupported(capability?.hasPrimaryRail)}")
            Text("secondary: ${percent(shade.state?.secondaryPercent)}${unsupported(capability?.hasSecondaryRail)}")
            Text("tilt: ${shade.state?.tiltPercent?.let { "$it%" } ?: "-"}${unsupported(capability?.hasTilt)}")
            Text("velocity (raw): ${shade.state?.velocityRaw ?: "-"}")

            val capabilitySuffix = if (shade.capabilities?.isKnownType == false) " (unknown typeId)" else ""
            Text("capability: ${capability?.name ?: "?"}$capabilitySuffix")
            Text("last seen: ${shade.lastSeenAt ?: "never"}")

            // The bytes every field above was decoded from. Company ID already
            // stripped, so index 0 here is offset 0 in docs/PROTOCOL.md §2.
            Text(
                text = "raw: ${shade.lastRawPayloadHex ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Battery needs a GATT connection, unlike everything above it, which
            // comes free from the advertisement. Connecting costs the shade
            // power, so it is on demand rather than polled (build order step 4;
            // the weekly sweep is step 10).
            Text(
                text = "battery: ${batteryText(shade)}",
                modifier = Modifier.padding(top = 4.dp),
            )
            if (batteryMessage != null) {
                Text(
                    text = batteryMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onReadBattery,
                enabled = !batteryReading,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (batteryReading) "Reading…" else "Read battery")
            }
        }
    }
}

/**
 * The bucket is not a charge percentage — the shades report roughly 10/50/100
 * for low/medium/high — so the level leads and the raw number follows in
 * brackets for anyone verifying against real hardware.
 */
private fun batteryText(shade: Shade): String {
    if (shade.mainsPowered) return "mains-powered"
    val bucket = shade.batteryBucket ?: return "not read yet"
    val level = when (batteryLevelOf(bucket)) {
        BatteryLevel.LOW -> "low"
        BatteryLevel.MEDIUM -> "medium"
        BatteryLevel.HIGH -> "high"
        BatteryLevel.UNKNOWN -> "unrecognised"
    }
    val readAt = shade.batteryReadAt?.let { " at $it" } ?: ""
    return "$level (raw $bucket)$readAt"
}

private fun percent(value: Double?): String =
    value?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "-"

/**
 * Marks a field the shade's capability says it does not have, so a decoded
 * value from an unused byte is not read as a real position. Null capability
 * (nothing decoded yet) annotates nothing.
 */
private fun unsupported(supported: Boolean?): String =
    if (supported == false) "  [not supported by capability]" else ""
