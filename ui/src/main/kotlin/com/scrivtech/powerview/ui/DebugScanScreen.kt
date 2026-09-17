package com.scrivtech.powerview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrivtech.powerview.data.Shade
import java.util.Locale

/**
 * Build order step 2: "Scanner + a raw debug screen listing MAC / RSSI /
 * decoded ShadeState / hex payload. Confirms your offsets against real
 * shades before any writes." RSSI and the raw hex payload aren't carried on
 * [Shade] today (they're per-advertisement, not per-shade state) — surfacing
 * them here means either adding a `lastRawPayloadHex`/`lastRssi` field to
 * [Shade] or observing [com.scrivtech.powerview.ble.ShadeScanner] directly
 * for this screen instead of going through the repository. Deferred until
 * step 2 is actually being driven against real hardware.
 *
 * What's here already: every decoded field ([Shade.state]) and the
 * capability lookup, which is the part worth confirming against a real
 * device advertisement first.
 */
@Composable
public fun DebugScanScreen(viewModel: ShadeListViewModel, modifier: Modifier = Modifier) {
    val shades by viewModel.shades.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shades, key = { it.macAddress }) { shade -> ShadeDebugRow(shade) }
    }
}

@Composable
private fun ShadeDebugRow(shade: Shade) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(shade.macAddress, style = MaterialTheme.typography.titleMedium)
            Text("homeId: ${shade.homeId ?: "?"}  typeId: ${shade.state?.typeId ?: "?"}")
            Text(
                "primary: ${shade.state?.primaryPercent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "-"}  " +
                    "secondary: ${shade.state?.secondaryPercent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "-"}  " +
                    "tilt: ${shade.state?.tiltPercent?.let { "$it%" } ?: "-"}",
            )
            val capabilitySuffix = if (shade.capabilities?.isKnownType == false) " (unknown typeId)" else ""
            Text("capability: ${shade.capabilities?.capability?.name ?: "?"}$capabilitySuffix")
            Text("last seen: ${shade.lastSeenAt ?: "never"}")
        }
    }
}
