package com.scrivtech.powerview.widget

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.scrivtech.powerview.ble.ShadeGattClient
import com.scrivtech.powerview.data.Command
import com.scrivtech.powerview.data.KeystreamStore
import com.scrivtech.powerview.data.ShadeAction
import com.scrivtech.powerview.data.ShadeStore
import com.scrivtech.powerview.protocol.CommandFrameBuilder
import com.scrivtech.powerview.protocol.FrameCipher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single funnel every command surface goes through — widget taps, the
 * Quick Settings tile, in-app buttons, notification actions. Per spec §2
 * ("Design rule for the whole command surface"): none of those own BLE code
 * directly, because BLE is slow and fails often, and centralizing here means
 * queuing, retry, and user feedback only need to be solved once.
 *
 * This is deliberately runnable from in-app buttons before any widget code
 * exists (build order step 8): [CommandWorker] and the Glance widgets both
 * call this same class.
 *
 * Nothing here is allowed to throw. Every caller is a background surface with
 * no UI attached — a widget tap or a `WorkManager` job — so an exception is
 * not something a user can be asked about; it just becomes a crash or an
 * opaque failed work item.
 */
public class ActionRunner(
    private val context: Context,
    private val shadeStore: ShadeStore,
    private val keystreamStore: KeystreamStore,
) {
    // Sequence counters are per-shade and in-memory only, so they reset on
    // process death. Whether the shade validates sequence monotonicity
    // (replay protection) or ignores it entirely is unconfirmed — see
    // docs/PROTOCOL.md §5. If it turns out to matter, persist this instead.
    private val sequenceCounters = ConcurrentHashMap<String, AtomicInteger>()

    /** Runs every [ShadeAction.commands] entry sequentially and reports which (if any) failed. */
    public suspend fun run(action: ShadeAction): ActionResult {
        val failed = action.commands.filterNot { runCommand(it) }.map { it.macAddress }
        return if (failed.isEmpty()) ActionResult.Success else ActionResult.Failed(failed)
    }

    /**
     * @return false on any failure: unknown shade (cold start with no
     *   persisted metadata), no keystream yet for the shade's home
     *   (onboarding not done, spec §2.4), missing `BLUETOOTH_CONNECT`,
     *   Bluetooth unavailable or switched off, a malformed stored MAC, or the
     *   GATT connect/discover/write sequence failing (most commonly
     *   out-of-range, spec §3.4). Callers needing to distinguish these should
     *   inspect [ShadeStore]/[KeystreamStore] themselves before calling — this
     *   method intentionally collapses them to a single failure for [run].
     */
    private suspend fun runCommand(command: Command): Boolean {
        // ShadeGattClient is annotated @SuppressLint("MissingPermission") and
        // documents that its caller must hold BLUETOOTH_CONNECT. This is that
        // caller, and it runs from surfaces with no UI to prompt from, so an
        // unchecked SecurityException here would surface as a crash rather than
        // a failed command.
        if (!ShadeGattClient.hasConnectPermission(context)) return false

        val metadata = shadeStore.shades.first()[command.macAddress] ?: return false
        val homeId = metadata.homeId ?: return false

        // A corrupt stored value throws out of the hex decode; treat it the
        // same as "no keystream yet" rather than taking the process down.
        val keystream = runCatching { keystreamStore.get(homeId) }.getOrNull() ?: return false

        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter
            ?: return false
        if (!adapter.isEnabled) return false

        // MACs come out of persisted JSON (ActionStore), so a corrupt or
        // hand-edited store reaches getRemoteDevice, which throws
        // IllegalArgumentException on anything that is not a well-formed
        // address. This used to sit outside the try and escape run().
        val device = runCatching {
            require(BluetoothAdapter.checkBluetoothAddress(command.macAddress)) {
                "malformed MAC address: ${command.macAddress}"
            }
            adapter.getRemoteDevice(command.macAddress)
        }.getOrNull() ?: return false

        val client = ShadeGattClient(context, device)

        return try {
            if (!client.connect()) return false
            if (!client.discoverServices()) return false

            val sequence = nextSequence(command.macAddress)
            val plaintext = CommandFrameBuilder.build(
                sequence = sequence,
                primaryPercent = command.primaryPercent,
                secondaryPercent = command.secondaryPercent,
                tiltPercent = command.tiltPercent,
            )
            val ciphertext = FrameCipher.xorWithKeystream(plaintext, keystream)
            client.writeCommand(ciphertext)
        } catch (e: SecurityException) {
            // The permission can be revoked between the check above and the
            // call itself; that is a failed command, not a crash.
            false
        } finally {
            // NonCancellable because disconnect() is the only thing that
            // releases the GATT client registration. If this coroutine is
            // cancelled (widget teardown, WorkManager stopping the worker), a
            // plain suspend call here would throw immediately and leak it.
            withContext(NonCancellable) { client.disconnect() }
        }
    }

    private fun nextSequence(macAddress: String): Byte =
        sequenceCounters
            .getOrPut(macAddress) { AtomicInteger(Byte.MIN_VALUE.toInt()) }
            .getAndIncrement()
            .toByte()
}
