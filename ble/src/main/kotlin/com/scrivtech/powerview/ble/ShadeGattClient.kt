package com.scrivtech.powerview.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * One GATT connection to one shade. See the spec's Android app architecture
 * §2.3. Owns exactly one [CommandQueue] so every call below runs fully
 * serialized — the Android BLE stack cannot handle overlapping operations.
 *
 * Lifecycle: [connect] -> [discoverServices] -> any number of
 * [writeCommand]/[readCharacteristic] -> [disconnect]. Callers should
 * disconnect after a short idle timeout (~10s per the spec) rather than
 * holding the connection open — shades are battery devices.
 *
 * `autoConnect = false` is used for [connect] deliberately: it gives faster,
 * more predictable latency for a foreground user action than Android's
 * auto-connect background-retry behavior, at the cost of not auto-reconnecting
 * if the shade drops out of range mid-session (acceptable — see the
 * out-of-range handling in the widget failure modes, spec §3.4).
 */
public class ShadeGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val queue = CommandQueue()
    private var gatt: BluetoothGatt? = null

    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var serviceDiscoveryDeferred: CompletableDeferred<Boolean>? = null
    private var writeDeferred: CompletableDeferred<Boolean>? = null
    private var readDeferred: CompletableDeferred<ByteArray?>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectDeferred?.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectDeferred?.complete(false)
                gatt = null
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            serviceDiscoveryDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Deprecated on API 33+ in favor of the value-carrying overload below,
        // but still the only callback invoked on API < 33.
        @Suppress("DEPRECATION")
        @Deprecated("Superseded by onCharacteristicRead(gatt, characteristic, value, status) on API 33+")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return // handled by the overload below
            readDeferred?.complete(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            readDeferred?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }
    }

    /** Connects and waits (up to [timeoutMillis]) for `STATE_CONNECTED`. Returns false on failure/timeout. */
    @SuppressLint("MissingPermission") // caller is required to have checked BLUETOOTH_CONNECT — see ShadeScanner.hasScanPermission for the parallel pattern
    public suspend fun connect(timeoutMillis: Long = 10_000): Boolean = queue.enqueue {
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitWithTimeout(deferred, timeoutMillis, default = false)
    }

    /** Must be called before any [writeCommand]/[readCharacteristic] — nothing is cached across connections. */
    @SuppressLint("MissingPermission")
    public suspend fun discoverServices(timeoutMillis: Long = 10_000): Boolean = queue.enqueue {
        val g = gatt ?: return@enqueue false
        val deferred = CompletableDeferred<Boolean>()
        serviceDiscoveryDeferred = deferred
        if (!g.discoverServices()) return@enqueue false
        awaitWithTimeout(deferred, timeoutMillis, default = false)
    }

    /**
     * Writes [frame] to [GattUuids.CHARACTERISTIC_COMMAND]. Caller is
     * responsible for encryption ([com.scrivtech.powerview.protocol.FrameCipher])
     * before calling this — this class only moves bytes.
     *
     * TODO(build order step 4/5): confirm on real hardware whether the
     * characteristic wants WRITE_TYPE_DEFAULT (with response) or
     * WRITE_TYPE_NO_RESPONSE — probe `characteristic.properties` and log
     * what's found, per spec §2.3.
     */
    @SuppressLint("MissingPermission")
    public suspend fun writeCommand(frame: ByteArray, timeoutMillis: Long = 10_000): Boolean = queue.enqueue {
        val g = gatt ?: return@enqueue false
        val characteristic = g.getService(GattUuids.SERVICE_SHADE)
            ?.getCharacteristic(GattUuids.CHARACTERISTIC_COMMAND)
            ?: return@enqueue false

        val writeType = if (
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        ) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val deferred = CompletableDeferred<Boolean>()
        writeDeferred = deferred

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(characteristic, frame, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = frame
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
        if (!started) return@enqueue false

        // WRITE_TYPE_NO_RESPONSE fires onCharacteristicWrite immediately with
        // a locally-determined status rather than a real ack from the shade,
        // so this still resolves promptly either way.
        awaitWithTimeout(deferred, timeoutMillis, default = false)
    }

    /** Generic characteristic read — used for [GattUuids.CHARACTERISTIC_BATTERY_LEVEL] and device-info characteristics. */
    @SuppressLint("MissingPermission")
    public suspend fun readCharacteristic(
        service: UUID,
        characteristic: UUID,
        timeoutMillis: Long = 10_000,
    ): ByteArray? = queue.enqueue {
        val g = gatt ?: return@enqueue null
        val target = g.getService(service)?.getCharacteristic(characteristic) ?: return@enqueue null

        val deferred = CompletableDeferred<ByteArray?>()
        readDeferred = deferred
        if (!g.readCharacteristic(target)) return@enqueue null

        awaitWithTimeout(deferred, timeoutMillis, default = null)
    }

    @SuppressLint("MissingPermission")
    public fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private suspend fun <T> awaitWithTimeout(deferred: CompletableDeferred<T>, timeoutMillis: Long, default: T): T =
        try {
            withTimeout(timeoutMillis) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            default
        }
}
