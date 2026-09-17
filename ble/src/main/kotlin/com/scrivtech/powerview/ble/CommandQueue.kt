package com.scrivtech.powerview.ble

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes GATT operations on one connection. Android's BLE stack allows
 * exactly one outstanding operation per `BluetoothGatt` — a second write
 * issued before the first one's callback fires is silently dropped or
 * returns `false`/`BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY`
 * depending on API level. See the spec's Android app architecture §2.3.
 *
 * One [CommandQueue] belongs to one [ShadeGattClient] (one connection);
 * don't share an instance across devices. [ShadeGattClient] owns pairing
 * each queued call with the matching `BluetoothGattCallback` completion via
 * a `CompletableDeferred`.
 */
public class CommandQueue {

    private val mutex = Mutex()

    /** Runs [op] with the queue's lock held, so no other queued op can start a GATT call until [op] completes. */
    public suspend fun <T> enqueue(op: suspend () -> T): T = mutex.withLock { op() }
}
