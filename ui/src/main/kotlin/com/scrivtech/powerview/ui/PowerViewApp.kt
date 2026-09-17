package com.scrivtech.powerview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scrivtech.powerview.data.ThemeMode

private const val ROUTE_SHADES = "shades"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_ACTIONS = "actions"
private const val ROUTE_DEBUG = "debug"
private const val ROUTE_SETTINGS = "settings"

/**
 * The app's root. Screen state is a saved route string plus an optional MAC
 * rather than a `NavHost`: at this size that is less code than wiring
 * navigation-compose would be, and it survives rotation just as well.
 * `navigation-compose` is in the version catalog but referenced by no module,
 * so its pin has never been resolved by a build — worth avoiding in the same
 * change that introduces new screens. Swap it in when the graph is big enough
 * to earn it; nothing here depends on staying hand-rolled.
 *
 * The action editor is not a route of its own. It is shown whenever
 * [ActionsViewModel] holds a draft, so "is an edit in progress" has exactly one
 * source of truth instead of a route and a draft that could disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PowerViewApp(
    viewModel: ShadeListViewModel,
    actionsViewModel: ActionsViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    tileActionId: String?,
    onTileActionChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var route by rememberSaveable { mutableStateOf(ROUTE_SHADES) }
    var selectedMac by rememberSaveable { mutableStateOf<String?>(null) }

    val shades by viewModel.shades.collectAsState()
    val savedMacAddresses by viewModel.savedMacAddresses.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val batteryReads by viewModel.batteryReads.collectAsState()
    val commands by viewModel.commands.collectAsState()

    val actions by actionsViewModel.actions.collectAsState()
    val runs by actionsViewModel.runs.collectAsState()
    val draft by actionsViewModel.draft.collectAsState()

    val selectedShade = selectedMac?.let { mac -> shades.firstOrNull { it.macAddress == mac } }

    // The selected shade can disappear underneath the detail screen — it is
    // forgotten, or the process restarted and it has not advertised yet. Fall
    // back to the list rather than rendering a detail screen with no subject.
    val currentRoute = if (route == ROUTE_DETAIL && selectedShade == null) ROUTE_SHADES else route

    val savedShades = shades.filter { it.macAddress in savedMacAddresses }
    val labelForMacAddress: (String) -> String = { mac ->
        shades.firstOrNull { it.macAddress == mac }?.label ?: mac
    }

    fun goToShades() {
        route = ROUTE_SHADES
        selectedMac = null
    }

    // An open editor is what Back closes first; only then does the route change.
    BackHandler(enabled = currentRoute != ROUTE_SHADES || draft != null) {
        if (draft != null) actionsViewModel.cancelEdit() else goToShades()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            draft != null && currentRoute == ROUTE_ACTIONS -> "Edit action"
                            currentRoute == ROUTE_DETAIL -> selectedShade?.label.orEmpty()
                            currentRoute == ROUTE_ACTIONS -> "Actions"
                            currentRoute == ROUTE_DEBUG -> "Raw scan"
                            currentRoute == ROUTE_SETTINGS -> "Settings"
                            else -> "Shades"
                        },
                    )
                },
                navigationIcon = {
                    if (currentRoute != ROUTE_SHADES || draft != null) {
                        TextButton(
                            onClick = {
                                if (draft != null) actionsViewModel.cancelEdit() else goToShades()
                            },
                        ) {
                            Text("Back")
                        }
                    }
                },
                actions = {
                    if (currentRoute == ROUTE_SHADES) {
                        // An overflow rather than a row of text buttons: there
                        // are three destinations now, and three labels do not
                        // fit an app bar on a phone. The project pulls in no
                        // icon dependency, so the trigger is a word.
                        var menuOpen by remember { mutableStateOf(false) }

                        TextButton(onClick = { menuOpen = true }) { Text("More") }

                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Actions") },
                                onClick = {
                                    menuOpen = false
                                    route = ROUTE_ACTIONS
                                },
                            )
                            // The debug screen stays reachable: it is the tool
                            // that confirmed the advertisement offsets against
                            // hardware, and the open questions in
                            // docs/PROTOCOL.md §8 mean it is not finished being
                            // useful.
                            DropdownMenuItem(
                                text = { Text("Raw scan") },
                                onClick = {
                                    menuOpen = false
                                    route = ROUTE_DEBUG
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuOpen = false
                                    route = ROUTE_SETTINGS
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        val content = Modifier.padding(contentPadding)

        when (currentRoute) {
            ROUTE_DEBUG -> DebugScanScreen(viewModel = viewModel, modifier = content)

            ROUTE_SETTINGS -> SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                actions = actions,
                tileActionId = tileActionId,
                onTileActionChange = onTileActionChange,
                modifier = content,
            )

            ROUTE_ACTIONS -> {
                val openDraft = draft
                if (openDraft != null) {
                    ActionEditorScreen(
                        draft = openDraft,
                        shades = savedShades,
                        canDelete = actions.any { it.id == openDraft.id },
                        onLabelChange = actionsViewModel::updateDraftLabel,
                        onIconChange = actionsViewModel::updateDraftIcon,
                        onFieldEnabled = actionsViewModel::setFieldEnabled,
                        onFieldPercent = actionsViewModel::setFieldPercent,
                        onSave = actionsViewModel::saveDraft,
                        onCancel = actionsViewModel::cancelEdit,
                        onDelete = {
                            actionsViewModel.deleteAction(openDraft.id)
                            actionsViewModel.cancelEdit()
                        },
                        modifier = content,
                    )
                } else {
                    ActionsScreen(
                        actions = actions,
                        runs = runs,
                        labelForMacAddress = labelForMacAddress,
                        canCreate = savedShades.isNotEmpty(),
                        onRun = actionsViewModel::runAction,
                        onEdit = { actionId ->
                            actionsViewModel.editAction(actionId, savedShades.map { it.macAddress })
                        },
                        onDelete = actionsViewModel::deleteAction,
                        onCreate = {
                            actionsViewModel.startNewAction(savedShades.map { it.macAddress })
                        },
                        modifier = content,
                    )
                }
            }

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
                        goToShades()
                    },
                    onForget = {
                        viewModel.forgetShade(shade.macAddress)
                        goToShades()
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
