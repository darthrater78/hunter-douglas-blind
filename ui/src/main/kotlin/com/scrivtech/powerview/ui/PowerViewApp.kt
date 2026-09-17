package com.scrivtech.powerview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private const val ROUTE_SHADES = "shades"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_DEBUG = "debug"

/**
 * The app's root. Screen state is a saved route string plus an optional MAC
 * rather than a `NavHost`: at this size that is less code than wiring
 * navigation-compose would be, and it survives rotation just as well.
 * `navigation-compose` is in the version catalog but referenced by no module,
 * so its pin has never been resolved by a build — worth avoiding in the same
 * change that introduces three new screens. Swap it in when the graph is big
 * enough to earn it; nothing here depends on staying hand-rolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PowerViewApp(viewModel: ShadeListViewModel, modifier: Modifier = Modifier) {
    var route by rememberSaveable { mutableStateOf(ROUTE_SHADES) }
    var selectedMac by rememberSaveable { mutableStateOf<String?>(null) }

    val shades by viewModel.shades.collectAsState()
    val savedMacAddresses by viewModel.savedMacAddresses.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val batteryReads by viewModel.batteryReads.collectAsState()
    val commands by viewModel.commands.collectAsState()

    val selectedShade = selectedMac?.let { mac -> shades.firstOrNull { it.macAddress == mac } }

    // The selected shade can disappear underneath the detail screen — it is
    // forgotten, or the process restarted and it has not advertised yet. Fall
    // back to the list rather than rendering a detail screen with no subject.
    val currentRoute = if (route == ROUTE_DETAIL && selectedShade == null) ROUTE_SHADES else route

    fun goToList() {
        route = ROUTE_SHADES
        selectedMac = null
    }

    BackHandler(enabled = currentRoute != ROUTE_SHADES) { goToList() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            ROUTE_DETAIL -> selectedShade?.label.orEmpty()
                            ROUTE_DEBUG -> "Raw scan"
                            else -> "Shades"
                        },
                    )
                },
                navigationIcon = {
                    if (currentRoute != ROUTE_SHADES) {
                        TextButton(onClick = { goToList() }) { Text("Back") }
                    }
                },
                actions = {
                    if (currentRoute == ROUTE_SHADES) {
                        // The debug screen stays reachable: it is the tool that
                        // confirmed the advertisement offsets against hardware,
                        // and the open questions in docs/PROTOCOL.md §8 mean it
                        // is not finished being useful.
                        TextButton(onClick = { route = ROUTE_DEBUG }) { Text("Raw scan") }
                    }
                },
            )
        },
    ) { contentPadding ->
        val content = Modifier.padding(contentPadding)

        when (currentRoute) {
            ROUTE_DEBUG -> DebugScanScreen(viewModel = viewModel, modifier = content)

            ROUTE_DETAIL -> {
                val shade = requireNotNull(selectedShade) // guarded by currentRoute above
                ShadeDetailScreen(
                    shade = shade,
                    isSetUp = shade.macAddress in savedMacAddresses,
                    batteryReading = shade.macAddress in batteryReads.inFlight,
                    batteryMessage = batteryReads.messages[shade.macAddress],
                    readiness = commands.readiness[shade.macAddress],
                    commandInFlight = shade.macAddress in commands.inFlight,
                    commandOutcome = commands.outcomes[shade.macAddress],
                    onSave = { label, room, mainsPowered ->
                        viewModel.saveShade(shade.macAddress, label, room, mainsPowered)
                        goToList()
                    },
                    onForget = {
                        viewModel.forgetShade(shade.macAddress)
                        goToList()
                    },
                    onReadBattery = { viewModel.readBattery(shade.macAddress) },
                    onSendPosition = { primary, secondary, tilt ->
                        viewModel.sendPosition(shade.macAddress, primary, secondary, tilt)
                    },
                    onRefreshReadiness = { viewModel.refreshReadiness(shade.macAddress) },
                    modifier = content,
                )
            }

            else -> ShadeListScreen(
                shades = shades,
                savedMacAddresses = savedMacAddresses,
                scanState = scanState,
                onRetryScan = viewModel::retryScan,
                onOpenShade = { mac ->
                    selectedMac = mac
                    route = ROUTE_DETAIL
                },
                modifier = content,
            )
        }
    }
}
