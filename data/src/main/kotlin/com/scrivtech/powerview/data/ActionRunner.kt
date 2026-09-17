package com.scrivtech.powerview.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.scrivtech.powerview.ble.ShadeGattClient
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
 * This lives in `:data` rather than `:widget` for the same reason
 * [BatteryReader] does: it is a GATT-backed domain service over `:ble`, not a
 * home-screen surface. `:ui` and `:widget` are peers that both drive it, so
 * neither should have to depend on the other to reach it.
 *
 * Nothing here is allowed to throw. Every caller may be a background surface
 * with no UI attached — a widget tap or a `WorkManager` job — so an exception
 * is not something a user can be asked about; it just becomes a crash or an
 * opaque failed work item. Failures are returned as [CommandOutcome] instead.
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
        val failures = mutableMapOf<String, CommandOutcome>()
        for (command in action.commands) {
            val outcome = send(command)
            if (outcome != CommandOutcome.Sent) failures[command.macAddress] = outcome
        }
        return if (failures.isEmpty()) ActionResult.Success else ActionResult.Failed(failures)
    }

    /**
     * Sends one [command] and reports precisely what happened. In-app callers
     * want this rather than [run]: a single shade's button can act on the
     * distinction between "no keystream yet" and "out of range", where a
     * widget can only draw a failure icon either way.
     */
    public suspend fun send(command: Command): CommandOutcome {
        val target = when (val resolution = resolve(command.macAddress)) {
            is Resolution.Blocked -> return resolution.reason
            is Resolution.Ready -> resolution
        }

        val client = ShadeGattClient(context, target.device)

        return try {
            if (!client.connect()) {
                return CommandOutcome.TransportFailed(CommandOutcome.TransportFailed.Stage.CONNECT)
            }
            if (!client.discoverServices()) {
                return CommandOutcome.TransportFailed(CommandOutcome.TransportFailed.Stage.DISCOVER)
            }

            val sequence = nextSequence(command.macAddress)
            val plaintext = CommandFrameBuilder.build(
                sequence = sequence,
                primaryPercent = command.primaryPercent,
                secondaryPercent = command.secondaryPercent,
                tiltPercent = command.tiltPercent,
            )
            val ciphertext = FrameCipher.xorWithKeystream(plaintext, target.keystream)

            if (client.writeCommand(ciphertext)) {
                CommandOutcome.Sent
            } else {
                CommandOutcome.TransportFailed(CommandOutcome.TransportFailed.Stage.WRITE)
            }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check in resolve() and
            // the call itself; that is a failed command, not a crash.
            CommandOutcome.TransportFailed(CommandOutcome.TransportFailed.Stage.PERMISSION_REVOKED)
        } finally {
            // NonCancellable because disconnect() is the only thing that
            // releases the GATT client registration. If this coroutine is
            // cancelled (widget teardown, WorkManager stopping the worker), a
            // plain suspend call here would throw immediately and leak it.
            withContext(NonCancellable) { client.disconnect() }
        }
    }

    /**
     * Everything [send] can determine without touching BLE, for a UI that
     * wants to explain (or disable) a button *before* it is pressed rather
     * than after. Returns null when a command would at least be attempted.
     *
     * This is the same code path [send] itself uses, deliberately: a readiness
     * check that drifts from the thing it predicts is worse than none.
     */
    public suspend fun checkReadiness(macAddress: String): CommandOutcome.NotAttempted? =
        (resolve(macAddress) as? Resolution.Blocked)?.reason

    // Not a data class: it carries a ByteArray, and the generated equals()
    // would compare it by identity while looking like it compared contents.
    private sealed interface Resolution {
        class Ready(val device: BluetoothDevice, val keystream: ByteArray) : Resolution
        class Blocked(val reason: CommandOutcome.NotAttempted) : Resolution
    }

    private suspend fun resolve(macAddress: String): Resolution {
        // ShadeGattClient is annotated @SuppressLint("MissingPermission") and
        // documents that its caller must hold BLUETOOTH_CONNECT. This is that
        // caller, and it runs from surfaces with no UI to prompt from, so an
        // unchecked SecurityException here would surface as a crash rather than
        // a failed command.
        if (!ShadeGattClient.hasConnectPermission(context)) {
            return Resolution.Blocked(CommandOutcome.NotAttempted.MissingConnectPermission)
        }

        val metadata = shadeStore.shades.first()[macAddress]
            ?: return Resolution.Blocked(CommandOutcome.NotAttempted.UnknownShade)
        val homeId = metadata.homeId
            ?: return Resolution.Blocked(CommandOutcome.NotAttempted.NoHomeId)

        // A corrupt stored value throws out of the hex decode; treat it the
        // same as "no keystream yet" rather than taking the process down.
        val keystream = runCatching { keystreamStore.get(homeId) }.getOrNull()
            ?: return Resolution.Blocked(CommandOutcome.NotAttempted.NoKeystream)

        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter
            ?: return Resolution.Blocked(CommandOutcome.NotAttempted.BluetoothOff)
        if (!adapter.isEnabled) {
            return Resolution.Blocked(CommandOutcome.NotAttempted.BluetoothOff)
        }

        // MACs come out of persisted JSON (ActionStore), so a corrupt or
        // hand-edited store reaches getRemoteDevice, which throws
        // IllegalArgumentException on anything that is not a well-formed
        // address.
        val device = runCatching {
            require(BluetoothAdapter.checkBluetoothAddress(macAddress)) {
                "malformed MAC address: $macAddress"
            }
            adapter.getRemoteDevice(macAddress)
        }.getOrNull() ?: return Resolution.Blocked(CommandOutcome.NotAttempted.MalformedAddress)

        return Resolution.Ready(device, keystream)
    }

    private fun nextSequence(macAddress: String): Byte =
        sequenceCounters
            .getOrPut(macAddress) { AtomicInteger(Byte.MIN_VALUE.toInt()) }
            .getAndIncrement()
            .toByte()
}
