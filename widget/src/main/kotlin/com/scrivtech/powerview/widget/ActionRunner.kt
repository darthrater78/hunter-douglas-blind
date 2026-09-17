package com.scrivtech.powerview.widget

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.scrivtech.powerview.ble.ShadeGattClient
import com.scrivtech.powerview.data.Command
import com.scrivtech.powerview.data.KeystreamStore
import com.scrivtech.powerview.data.ShadeAction
import com.scrivtech.powerview.data.ShadeStore
import com.scrivtech.powerview.protocol.CommandFrameBuilder
import com.scrivtech.powerview.protocol.FrameCipher
import kotlinx.coroutines.flow.first
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
     *   (onboarding not done, spec §2.4), Bluetooth unavailable, or the GATT
     *   connect/discover/write sequence failing (most commonly out-of-range,
     *   spec §3.4). Callers needing to distinguish these should inspect
     *   [ShadeStore]/[KeystreamStore] themselves before calling — this
     *   method intentionally collapses them to a single failure for [run].
     */
    private suspend fun runCommand(command: Command): Boolean {
        val metadata = shadeStore.shades.first()[command.macAddress] ?: return false
        val homeId = metadata.homeId ?: return false
        val keystream = keystreamStore.get(homeId) ?: return false

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val device = adapter.getRemoteDevice(command.macAddress)
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
        } finally {
            client.disconnect()
        }
    }

    private fun nextSequence(macAddress: String): Byte =
        sequenceCounters
            .getOrPut(macAddress) { AtomicInteger(Byte.MIN_VALUE.toInt()) }
            .getAndIncrement()
            .toByte()
}
