package com.scrivtech.powerview.data

import com.scrivtech.powerview.ble.RawAdvertisement
import com.scrivtech.powerview.ble.ShadeScanner
import com.scrivtech.powerview.protocol.Capabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Health of the underlying advertisement scan, for the UI to react to (e.g. prompt for a missing permission). */
public sealed interface ScanState {
    public data object Scanning : ScanState
    public data object Stopped : ScanState
    public data class Failed(public val reason: Throwable) : ScanState
}

/**
 * Single source of truth for shade state: merges the live advertisement
 * stream ([ShadeScanner]) with persisted metadata ([ShadeStore]) into one
 * `StateFlow<Map<macAddress, Shade>>`, per the spec's Android app
 * architecture §2.
 *
 * Persisted metadata seeds entries even for shades that haven't been seen
 * live in this process — this is what makes a cold-started widget action
 * work (spec §3.4, "Cold start"): a widget targets a MAC directly and must
 * not depend on a scan having run first.
 *
 * The caller supplies [scope] (an application-scoped `CoroutineScope`) and
 * owns starting/stopping the underlying scan by cancelling it — this class
 * does not itself decide when scanning should be active.
 */
public class ShadeRepository(
    private val scanner: ShadeScanner,
    private val shadeStore: ShadeStore,
    private val scope: CoroutineScope,
) {
    private val _shades = MutableStateFlow<Map<String, Shade>>(emptyMap())
    public val shades: StateFlow<Map<String, Shade>> = _shades.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Stopped)
    public val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    init {
        startScan()
        scope.launch { shadeStore.shades.collect(::applyMetadata) }
    }

    /**
     * (Re)starts the scan collector. The initial attempt in [init] typically
     * runs before a runtime permission prompt (API 31+) has been answered,
     * so it commonly ends in [ScanState.Failed] immediately — call this again
     * once `BLUETOOTH_SCAN` is granted (e.g. from the
     * `ActivityResultContracts.RequestMultiplePermissions` callback) to
     * actually pick up scanning. Safe to call repeatedly; cancels any
     * in-flight attempt first.
     */
    public fun startScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            _scanState.value = ScanState.Scanning
            scanner.scan()
                .catch { error ->
                    // A missing BLUETOOTH_SCAN permission or a disabled adapter
                    // surfaces here as a flow exception (see ShadeScanner.scan).
                    // Without this catch it would propagate as an uncaught
                    // exception in this application-scoped coroutine and could
                    // crash the app before the UI ever gets to ask for the
                    // permission — so it's turned into observable state instead.
                    _scanState.value = ScanState.Failed(error)
                }
                .collect(::applyAdvertisement)
            _scanState.value = ScanState.Stopped
        }
    }

    private fun applyAdvertisement(advertisement: RawAdvertisement) {
        _shades.update { current ->
            val capability = Capabilities.forTypeId(advertisement.shadeState.typeId)
            val existing = current[advertisement.macAddress]
            val updated = (existing ?: blankShade(advertisement.macAddress)).copy(
                state = advertisement.shadeState,
                capabilities = capability,
                homeId = advertisement.shadeState.homeId,
                lastSeenAt = Instant.ofEpochMilli(advertisement.timestampMillis),
            )
            current + (advertisement.macAddress to updated)
        }
    }

    private fun applyMetadata(metadataByMac: Map<String, ShadeMetadata>) {
        _shades.update { current ->
            val merged = current.toMutableMap()
            for ((mac, meta) in metadataByMac) {
                val existing = merged[mac] ?: blankShade(mac)
                merged[mac] = existing.copy(
                    label = meta.label,
                    room = meta.room,
                    homeId = meta.homeId ?: existing.homeId,
                    mainsPowered = meta.mainsPowered,
                    batteryBucket = meta.batteryBucket ?: existing.batteryBucket,
                    batteryReadAt = meta.batteryReadAtEpochMillis?.let(Instant::ofEpochMilli) ?: existing.batteryReadAt,
                )
            }
            merged
        }
    }

    private fun blankShade(macAddress: String) = Shade(
        macAddress = macAddress,
        label = macAddress,
        room = null,
        homeId = null,
        state = null,
        capabilities = null,
        lastSeenAt = null,
        batteryBucket = null,
        batteryReadAt = null,
        mainsPowered = false,
    )
}
