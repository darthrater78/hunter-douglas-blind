package com.scrivtech.powerview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrivtech.powerview.data.BatteryReadResult
import com.scrivtech.powerview.data.BatteryReader
import com.scrivtech.powerview.data.ScanState
import com.scrivtech.powerview.data.Shade
import com.scrivtech.powerview.data.ShadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Backs [DebugScanScreen] (build order step 2) and, later, the real shade
 * list. Deliberately thin — it does no BLE or persistence work itself, only
 * reshapes [ShadeRepository.shades] for display and delegates the battery read.
 */
public class ShadeListViewModel(
    private val repository: ShadeRepository,
    private val batteryReader: BatteryReader,
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

    private val _batteryReads = MutableStateFlow(BatteryReadUiState())
    public val batteryReads: StateFlow<BatteryReadUiState> = _batteryReads.asStateFlow()

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

    public class Factory(
        private val repository: ShadeRepository,
        private val batteryReader: BatteryReader,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ShadeListViewModel::class.java))
            return ShadeListViewModel(repository, batteryReader) as T
        }
    }
}
