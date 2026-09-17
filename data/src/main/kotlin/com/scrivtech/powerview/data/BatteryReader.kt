package com.scrivtech.powerview.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.scrivtech.powerview.ble.GattUuids
import com.scrivtech.powerview.ble.ShadeGattClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Reads a shade's battery level over GATT — build order step 4, spec §2.5.
 *
 * Unlike commanding a position, this needs **no keystream**: the battery level
 * lives on the standard GATT Battery Service (`0x180F` / `0x2A19`), which is
 * unencrypted. It does need a connection, though, which is why it is not part
 * of the advertisement read path.
 *
 * The value is a percentage, 0..100, per the Bluetooth SIG's definition of
 * `0x2A19`. `docs/PROTOCOL.md` §1 previously described it as a coarse
 * 10/50/100 bucket, following the openHAB binding's behaviour; a real Duette
 * TDBU returned 65, which is not one of those buckets, so that claim has been
 * corrected. [batteryLevelOf] still reduces it to three states for display,
 * but the underlying number is a real reading and is kept.
 *
 * Results are persisted to [ShadeStore] so a reading survives process death
 * and can be shown without reconnecting — shades are battery devices and
 * connecting to one costs it power, so this is deliberately on demand rather
 * than polled. The weekly sweep and low-battery notifications (build order
 * step 10) are a scheduled caller on top of this class, not a change to it.
 */
public class BatteryReader(
    private val context: Context,
    private val shadeStore: ShadeStore,
) {

    /**
     * Connects to [macAddress], reads the battery characteristic, persists the
     * result and disconnects. Never throws — every failure is a
     * [BatteryReadResult.Failed] or [BatteryReadResult.NotSupported], because
     * the callers are UI and background surfaces that cannot act on an
     * exception.
     */
    public suspend fun read(macAddress: String): BatteryReadResult {
        if (!ShadeGattClient.hasConnectPermission(context)) {
            return BatteryReadResult.Failed("BLUETOOTH_CONNECT not granted")
        }

        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter
            ?: return BatteryReadResult.Failed("no Bluetooth adapter")
        if (!adapter.isEnabled) return BatteryReadResult.Failed("Bluetooth is off")

        val device = runCatching {
            require(BluetoothAdapter.checkBluetoothAddress(macAddress)) { "malformed MAC address" }
            adapter.getRemoteDevice(macAddress)
        }.getOrNull() ?: return BatteryReadResult.Failed("malformed MAC address")

        val client = ShadeGattClient(context, device)
        return try {
            if (!client.connect()) return BatteryReadResult.Failed("could not connect (out of range?)")
            if (!client.discoverServices()) return BatteryReadResult.Failed("service discovery failed")

            val value = client.readCharacteristic(
                GattUuids.SERVICE_BATTERY,
                GattUuids.CHARACTERISTIC_BATTERY_LEVEL,
            )
            // A null/empty read here is the expected answer for a mains-powered
            // shade: whether Gen 3 hardwired shades expose 0x2A19 at all is one
            // of the open questions in docs/PROTOCOL.md §8, so this is reported
            // as "not supported" rather than as a failure.
            if (value == null || value.isEmpty()) return BatteryReadResult.NotSupported

            val percent = value[0].toInt() and 0xFF
            val readAt = Instant.now()
            persist(macAddress, percent, readAt)
            BatteryReadResult.Success(percent, readAt)
        } catch (e: SecurityException) {
            BatteryReadResult.Failed("BLUETOOTH_CONNECT revoked mid-read")
        } finally {
            // As in ActionRunner: disconnect() is the only thing that releases
            // the GATT client registration, so it must survive cancellation.
            withContext(NonCancellable) { client.disconnect() }
        }
    }

    private suspend fun persist(macAddress: String, percent: Int, readAt: Instant) {
        val existing = shadeStore.shades.first()[macAddress]
        val metadata = existing?.copy(
            batteryPercent = percent,
            batteryReadAtEpochMillis = readAt.toEpochMilli(),
        ) ?: ShadeMetadata(
            // Seen live but never labelled: fall back to the MAC, matching how
            // ShadeRepository names a shade it has no metadata for.
            macAddress = macAddress,
            label = macAddress,
            batteryPercent = percent,
            batteryReadAtEpochMillis = readAt.toEpochMilli(),
        )
        shadeStore.upsert(metadata)
    }
}

/** Outcome of one [BatteryReader.read]. */
public sealed interface BatteryReadResult {
    public data class Success(val percent: Int, val readAt: Instant) : BatteryReadResult

    /** The shade exposes no battery characteristic — expected for a mains-powered unit. */
    public data object NotSupported : BatteryReadResult

    public data class Failed(val reason: String) : BatteryReadResult
}

/**
 * A coarse reading of the battery percentage, for surfaces that want a state
 * rather than a number. A value outside 0..100 is [UNKNOWN] rather than
 * clamped, so a firmware change that starts reporting something other than a
 * percentage is visible instead of silently mislabelled.
 */
public enum class BatteryLevel { LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * Maps a battery percentage to its [BatteryLevel]. [LOW_BATTERY_PERCENT] is
 * the threshold the step-10 sweep will alert on, so it is defined once here
 * rather than being picked again in the notification code.
 */
public fun batteryLevelOf(percent: Int?): BatteryLevel = when (percent) {
    null -> BatteryLevel.UNKNOWN
    in 0..LOW_BATTERY_PERCENT -> BatteryLevel.LOW
    in (LOW_BATTERY_PERCENT + 1)..60 -> BatteryLevel.MEDIUM
    in 61..100 -> BatteryLevel.HIGH
    else -> BatteryLevel.UNKNOWN
}

/** At or below this percentage a shade is considered low (spec §2.5). */
public const val LOW_BATTERY_PERCENT: Int = 20
