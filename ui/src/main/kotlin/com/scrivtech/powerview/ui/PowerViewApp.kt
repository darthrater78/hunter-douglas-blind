package com.scrivtech.powerview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.scrivtech.powerview.data.SweepInterval
import com.scrivtech.powerview.data.ThemeMode

private const val ROUTE_SHADES = "shades"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_ACTIONS = "actions"
private const val ROUTE_DEBUG = "debug"
private const val ROUTE_SETTINGS = "settings"

/** One bottom-bar destination: the route it opens and its outlined/filled icon pair. */
private data class Tab(
    val route: String,
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

private val TABS = listOf(
    Tab(ROUTE_SHADES, "Shades", R.drawable.ic_roller_shades, R.drawable.ic_roller_shades_fill1),
    Tab(ROUTE_ACTIONS, "Actions", R.drawable.ic_play_circle, R.drawable.ic_play_circle_fill1),
    Tab(ROUTE_SETTINGS, "Settings", R.drawable.ic_settings, R.drawable.ic_settings_fill1),
)

/**
 * The app's root. Screen state is a saved route string plus an optional MAC
 * rather than a `NavHost`: at this size that is less code than wiring
 * navigation-compose would be, and it survives rotation just as well.
 * `navigation-compose` is in the version catalog but referenced by no module,
 * so its pin has never been resolved by a build — worth avoiding in the same
 * change that introduces new screens. Swap it in when the graph is big enough
 * to earn it; nothing here depends on staying hand-rolled.
 *
 * Shades, Actions and Settings are the three top-level destinations, on a
 * bottom bar. Everything else is pushed on top of one of them and hides the
 * bar while it is open: a shade's detail over Shades, the raw scan over
 * Settings (it is a hardware-verification tool, not somewhere a shade owner
 * goes daily, so it lives in Settings' Developer section rather than on the
 * bar), and the action editor over Actions. Back leaves a pushed screen for
 * the tab under it, and leaves Actions or Settings for Shades.
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
    sweepInterval: SweepInterval,
    onSweepIntervalChange: (SweepInterval) -> Unit,
    onSweepNow: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
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

    val editing = draft != null && currentRoute == ROUTE_ACTIONS
    val pushed = editing || currentRoute == ROUTE_DETAIL || currentRoute == ROUTE_DEBUG

    // An open editor is what Back closes first; only then does the route change.
    fun goBack() {
        when {
            draft != null -> actionsViewModel.cancelEdit()
            currentRoute == ROUTE_DEBUG -> route = ROUTE_SETTINGS
            else -> goToShades()
        }
    }

    BackHandler(enabled = currentRoute != ROUTE_SHADES || draft != null) { goBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editing -> "Edit action"
                            currentRoute == ROUTE_DETAIL -> selectedShade?.label.orEmpty()
                            currentRoute == ROUTE_ACTIONS -> "Actions"
                            currentRoute == ROUTE_DEBUG -> "Raw scan"
                            currentRoute == ROUTE_SETTINGS -> "Settings"
                            else -> "Shades"
                        },
                    )
                },
                navigationIcon = {
                    if (pushed) {
                        IconButton(onClick = ::goBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!pushed) {
                NavigationBar {
                    for (tab in TABS) {
                        val selected = tab.route == currentRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                route = tab.route
                                selectedMac = null
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
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
                sweepInterval = sweepInterval,
                onSweepIntervalChange = onSweepIntervalChange,
                onSweepNow = onSweepNow,
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
                onOpenSystemNotificationSettings = onOpenSystemNotificationSettings,
                onOpenRawScan = { route = ROUTE_DEBUG },
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
