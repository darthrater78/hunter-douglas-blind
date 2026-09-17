package com.scrivtech.powerview.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
 * holding the connection open — shades are battery devices. [disconnect] is
 * mandatory, not optional: it is the only thing that releases the underlying
 * GATT client registration, and Android caps those per process.
 *
 * `autoConnect = false` is used for [connect] deliberately: it gives faster,
 * more predictable latency for a foreground user action than Android's
 * auto-connect background-retry behavior, at the cost of not auto-reconnecting
 * if the shade drops out of range mid-session (acceptable — see the
 * out-of-range handling in the widget failure modes, spec §3.4).
 *
 * Every field below is `@Volatile` because the `BluetoothGattCallback` runs on
 * a Binder thread while the suspend functions run on whatever dispatcher the
 * caller used. Without it there is no happens-before edge between the two, and
 * the callback can observe a stale reference.
 */
public class ShadeGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val queue = CommandQueue()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var connectDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var serviceDiscoveryDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var writeDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var readDeferred: CompletableDeferred<ByteArray?>? = null

    /**
     * Set when an operation times out. The Android BLE API has no way to cancel
     * an outstanding GATT operation, so once one has timed out this connection
     * can never be known to be idle again — issuing the next one would put two
     * in flight, which is precisely what [CommandQueue] exists to prevent.
     * Every operation fails fast from then on until [disconnect] resets it.
     */
    @Volatile
    private var faulted: Boolean = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connectDeferred?.complete(true)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // Deliberately does NOT null out `gatt`. The BluetoothGatt
                    // still holds a client registration that only close()
                    // releases, and disconnect() is the only caller of close() —
                    // dropping the reference here leaked one registration per
                    // failed or dropped connection. Android allows a limited
                    // number per process (historically 32), after which every
                    // connectGatt in the app fails until the process restarts.
                    // Out-of-range failures are the expected case (spec §3.4),
                    // so that ceiling is reachable in normal use.
                    connectDeferred?.complete(false)
                    // Nothing else on this connection can ever be answered now;
                    // fail the waiters immediately instead of letting each one
                    // burn its full timeout.
                    serviceDiscoveryDeferred?.complete(false)
                    writeDeferred?.complete(false)
                    readDeferred?.complete(null)
                }
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

    /**
     * Connects and waits (up to [timeoutMillis]) for `STATE_CONNECTED`. Returns
     * false on failure/timeout. Whatever the result, the caller must still call
     * [disconnect] — a failed connect leaves a registration to release.
     */
    @SuppressLint("MissingPermission") // caller is required to have checked BLUETOOTH_CONNECT — see ShadeScanner.hasScanPermission for the parallel pattern
    public suspend fun connect(timeoutMillis: Long = 10_000): Boolean = queue.enqueue {
        if (faulted) return@enqueue false

        // Reconnecting on the same instance without releasing the previous
        // registration would leak it just as surely as never disconnecting.
        gatt?.let {
            it.disconnect()
            it.close()
        }

        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: return@enqueue false
        awaitWithTimeout(deferred, timeoutMillis, default = false)
            .also { connectDeferred = null }
    }

    /** Must be called before any [writeCommand]/[readCharacteristic] — nothing is cached across connections. */
    @SuppressLint("MissingPermission")
    public suspend fun discoverServices(timeoutMillis: Long = 10_000): Boolean = queue.enqueue {
        if (faulted) return@enqueue false
        val g = gatt ?: return@enqueue false
        val deferred = CompletableDeferred<Boolean>()
        serviceDiscoveryDeferred = deferred
        if (!g.discoverServices()) {
            serviceDiscoveryDeferred = null
            return@enqueue false
        }
        awaitWithTimeout(deferred, timeoutMillis, default = false)
            .also { serviceDiscoveryDeferred = null }
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
        if (faulted) return@enqueue false
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
        if (!started) {
            writeDeferred = null
            return@enqueue false
        }

        // WRITE_TYPE_NO_RESPONSE fires onCharacteristicWrite immediately with
        // a locally-determined status rather than a real ack from the shade,
        // so this still resolves promptly either way.
        awaitWithTimeout(deferred, timeoutMillis, default = false)
            .also { writeDeferred = null }
    }

    /** Generic characteristic read — used for [GattUuids.CHARACTERISTIC_BATTERY_LEVEL] and device-info characteristics. */
    @SuppressLint("MissingPermission")
    public suspend fun readCharacteristic(
        service: UUID,
        characteristic: UUID,
        timeoutMillis: Long = 10_000,
    ): ByteArray? = queue.enqueue {
        if (faulted) return@enqueue null
        val g = gatt ?: return@enqueue null
        val target = g.getService(service)?.getCharacteristic(characteristic) ?: return@enqueue null

        val deferred = CompletableDeferred<ByteArray?>()
        readDeferred = deferred
        if (!g.readCharacteristic(target)) {
            readDeferred = null
            return@enqueue null
        }

        awaitWithTimeout(deferred, timeoutMillis, default = null)
            .also { readDeferred = null }
    }

    /**
     * Releases the connection and its GATT client registration. Safe to call
     * more than once, and safe to call after a failed [connect] — that is the
     * case that matters most, since a failed connect still holds a registration.
     *
     * Goes through the same [CommandQueue] as every other operation so it
     * cannot close the connection out from under an in-flight write. Callers
     * invoking this from a `finally` should wrap it in
     * `withContext(NonCancellable)`, or a cancelled coroutine will skip it and
     * leak the registration — see `ActionRunner.runCommand`.
     */
    @SuppressLint("MissingPermission")
    public suspend fun disconnect(): Unit = queue.enqueue {
        val g = gatt
        gatt = null
        connectDeferred = null
        serviceDiscoveryDeferred = null
        writeDeferred = null
        readDeferred = null
        faulted = false
        if (g != null) {
            g.disconnect()
            g.close()
        }
    }

    public companion object {
        /**
         * Every method on this class is annotated `@SuppressLint("MissingPermission")`
         * and requires the caller to hold `BLUETOOTH_CONNECT`. This is that check,
         * living next to the API it guards so each caller does not reinvent it —
         * the mirror of [ShadeScanner.hasScanPermission].
         *
         * `BLUETOOTH_CONNECT` is only a runtime permission from API 31; below
         * that the legacy install-time `BLUETOOTH` permission in the manifest
         * covers it.
         */
        public fun hasConnectPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }

    private suspend fun <T> awaitWithTimeout(deferred: CompletableDeferred<T>, timeoutMillis: Long, default: T): T =
        try {
            withTimeout(timeoutMillis) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // See [faulted]: the operation is still outstanding somewhere in the
            // stack and cannot be cancelled, so this connection is finished.
            faulted = true
            default
        }
}
