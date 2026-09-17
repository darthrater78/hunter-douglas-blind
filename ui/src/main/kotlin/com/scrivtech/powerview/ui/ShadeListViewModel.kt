package com.scrivtech.powerview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrivtech.powerview.data.ActionRunner
import com.scrivtech.powerview.data.BatteryReadResult
import com.scrivtech.powerview.data.BatteryReader
import com.scrivtech.powerview.data.Command
import com.scrivtech.powerview.data.CommandOutcome
import com.scrivtech.powerview.data.ScanState
import com.scrivtech.powerview.data.Shade
import com.scrivtech.powerview.data.ShadeMetadata
import com.scrivtech.powerview.data.ShadeRepository
import com.scrivtech.powerview.data.ShadeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Transient, per-shade state for the battery read (build order step 4). A
 * *successful* read is not in here: it is persisted by [BatteryReader] and
 * arrives back through [ShadeRepository.shades] like any other metadata, so
 * only the in-flight set and the last failure need holding in memory.
 */
public data class BatteryReadUiState(
    public val inFlight: Set<String> = emptySet(),
    public val messages: Map<String, String> = emptyMap(),
)

/**
 * Whether a shade can be commanded at all, as far as can be known without
 * touching BLE. Worth showing before a button is pressed rather than after:
 * every [Readiness.Blocked] reason is something the user has to go and fix, so
 * a button that looks live and then explains itself wastes a trip to the shade.
 */
public sealed interface Readiness {
    public data object Checking : Readiness
    public data object Ready : Readiness
    public data class Blocked(public val reason: CommandOutcome.NotAttempted) : Readiness
}

/** Transient per-shade state for in-app commands (build order step 8). */
public data class CommandUiState(
    public val inFlight: Set<String> = emptySet(),
    public val outcomes: Map<String, CommandOutcome> = emptyMap(),
    public val readiness: Map<String, Readiness> = emptyMap(),
)

/**
 * Backs [DebugScanScreen] (build order step 2) and the shade list and detail
 * screens (step 7), including the in-app command buttons of step 8.
 * Deliberately thin — it does no BLE or persistence work itself, only reshapes
 * [ShadeRepository.shades] for display and delegates to [BatteryReader] and
 * [ActionRunner].
 */
public class ShadeListViewModel(
    private val repository: ShadeRepository,
    private val batteryReader: BatteryReader,
    private val shadeStore: ShadeStore,
    private val actionRunner: ActionRunner,
) : ViewModel() {

    public val shades: StateFlow<List<Shade>> = repository.shades
        .map { byMac -> byMac.values.sortedBy { it.label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /**
     * Health of the underlying scan. Surfaced so the UI can tell "no shades in
     * range" apart from "permission denied" and "Bluetooth is off" — all three
     * previously rendered as the same empty screen, which left the debug screen
     * unable to explain the one thing it exists to explain.
     */
    public val scanState: StateFlow<ScanState> = repository.scanState

    /**
     * MACs that have a persisted [ShadeMetadata] row, i.e. shades the user has
     * actually set up. [shades] deliberately cannot answer this: it merges live
     * advertisements with stored metadata, so a shade merely seen in a scan
     * looks the same as one that was named and saved. The list screen needs the
     * difference to separate "your shades" from "found nearby, not set up yet".
     */
    public val savedMacAddresses: StateFlow<Set<String>> = shadeStore.shades
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptySet())

    private val _batteryReads = MutableStateFlow(BatteryReadUiState())
    public val batteryReads: StateFlow<BatteryReadUiState> = _batteryReads.asStateFlow()

    private val _commands = MutableStateFlow(CommandUiState())
    public val commands: StateFlow<CommandUiState> = _commands.asStateFlow()

    /** Re-attempts the scan, e.g. after the user grants the permission or enables Bluetooth. */
    public fun retryScan() {
        repository.startScan()
    }

    /**
     * Reads [macAddress]'s battery over GATT. Ignores a tap on a shade already
     * being read: connecting costs the shade power, and the second connection
     * would queue behind the first anyway.
     */
    public fun readBattery(macAddress: String) {
        if (macAddress in _batteryReads.value.inFlight) return

        _batteryReads.update {
            it.copy(inFlight = it.inFlight + macAddress, messages = it.messages - macAddress)
        }

        viewModelScope.launch {
            val message = when (val result = batteryReader.read(macAddress)) {
                is BatteryReadResult.Success -> null // persisted; arrives via repository.shades
                BatteryReadResult.NotSupported ->
                    "No battery characteristic — mains-powered shade?"
                is BatteryReadResult.Failed -> result.reason
            }
            _batteryReads.update {
                it.copy(
                    inFlight = it.inFlight - macAddress,
                    messages = if (message == null) it.messages - macAddress else it.messages + (macAddress to message),
                )
            }
        }
    }

    /**
     * Saves the user-editable metadata for [macAddress], preserving everything
     * the user does not edit.
     *
     * The subtle part is [ShadeMetadata.homeId]. `homeId` only ever arrives on
     * an advertisement, and it is what [com.scrivtech.powerview.data.ActionRunner]
     * uses to find the keystream for a command — so a shade saved without one
     * can never be commanded, failing as `NotAttempted.NoHomeId` forever. This
     * is the moment it has to be captured: the user is naming a shade they can
     * currently see, so the live value is available right now and might not be
     * later. The same goes for the decoded capability, which decides which
     * controls the detail screen offers.
     */
    public fun saveShade(macAddress: String, label: String, room: String?, mainsPowered: Boolean) {
        viewModelScope.launch {
            val stored = shadeStore.shades.first()[macAddress]
            val live = repository.shades.value[macAddress]

            shadeStore.upsert(
                ShadeMetadata(
                    macAddress = macAddress,
                    label = label.trim().ifBlank { macAddress },
                    room = room?.trim()?.ifBlank { null },
                    homeId = live?.homeId ?: stored?.homeId,
                    capabilityId = live?.capabilities?.capability?.id ?: stored?.capabilityId,
                    lastSeenAtEpochMillis = live?.lastSeenAt?.toEpochMilli()
                        ?: stored?.lastSeenAtEpochMillis,
                    batteryPercent = stored?.batteryPercent,
                    batteryReadAtEpochMillis = stored?.batteryReadAtEpochMillis,
                    mainsPowered = mainsPowered,
                ),
            )
        }
    }

    /**
     * Drops the stored metadata for [macAddress]. The shade does not disappear
     * from [shades] if it is still advertising — it reverts to an un-set-up
     * shade, which is the honest result: forgetting is an app-side action and
     * cannot un-pair anything on the hardware.
     */
    public fun forgetShade(macAddress: String) {
        viewModelScope.launch { shadeStore.remove(macAddress) }
    }

    /**
     * Asks [ActionRunner] whether [macAddress] could be commanded right now.
     * Cheap — it touches storage and the Bluetooth adapter, never the shade —
     * so the detail screen can call it on open and again after any command.
     */
    public fun refreshReadiness(macAddress: String) {
        _commands.update { it.copy(readiness = it.readiness + (macAddress to Readiness.Checking)) }

        viewModelScope.launch {
            val blocked = actionRunner.checkReadiness(macAddress)
            val readiness = blocked?.let(Readiness::Blocked) ?: Readiness.Ready
            _commands.update { it.copy(readiness = it.readiness + (macAddress to readiness)) }
        }
    }

    /**
     * Sends one position command to one shade — build order step 8's "driven
     * from in-app buttons first".
     *
     * Null fields leave that rail or the tilt untouched, which is a property of
     * the frame itself (`CommandFrameBuilder` writes an explicit unset
     * sentinel), not something simulated here. A second press while one is in
     * flight is ignored rather than queued: these take seconds, and stacking
     * them up behind each other only makes the shade fight itself.
     */
    public fun sendPosition(
        macAddress: String,
        primaryPercent: Double? = null,
        secondaryPercent: Double? = null,
        tiltPercent: Int? = null,
    ) {
        if (macAddress in _commands.value.inFlight) return

        _commands.update {
            it.copy(
                inFlight = it.inFlight + macAddress,
                outcomes = it.outcomes - macAddress,
            )
        }

        viewModelScope.launch {
            val outcome = actionRunner.send(
                Command(
                    macAddress = macAddress,
                    primaryPercent = primaryPercent,
                    secondaryPercent = secondaryPercent,
                    tiltPercent = tiltPercent,
                ),
            )

            _commands.update {
                it.copy(
                    inFlight = it.inFlight - macAddress,
                    outcomes = it.outcomes + (macAddress to outcome),
                )
            }

            // A command can fail for a reason that also changes readiness —
            // Bluetooth switched off mid-session, the permission revoked — so
            // re-check rather than leaving a stale "Ready" next to a failure.
            refreshReadiness(macAddress)
        }
    }

    /** Clears the last command result for [macAddress], e.g. when its message is dismissed. */
    public fun clearCommandOutcome(macAddress: String) {
        _commands.update { it.copy(outcomes = it.outcomes - macAddress) }
    }

    public class Factory(
        private val repository: ShadeRepository,
        private val batteryReader: BatteryReader,
        private val shadeStore: ShadeStore,
        private val actionRunner: ActionRunner,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ShadeListViewModel::class.java))
            return ShadeListViewModel(repository, batteryReader, shadeStore, actionRunner) as T
        }
    }
}
