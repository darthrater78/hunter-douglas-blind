package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.ActionResult
import com.scrivtech.powerview.data.BatteryLevel
import com.scrivtech.powerview.data.CommandOutcome
import com.scrivtech.powerview.data.Shade
import com.scrivtech.powerview.data.ShadeAction
import com.scrivtech.powerview.data.batteryLevelOf
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Shown in place of a room name for a saved shade the user has not filed anywhere. */
internal const val NO_ROOM_LABEL: String = "No room"

/**
 * `0x2A19` is a percentage (see `docs/PROTOCOL.md` §1 — this was previously
 * documented as a coarse 10/50/100 bucket and corrected against real
 * hardware), so the number leads and the coarse level follows it.
 */
internal fun batteryText(shade: Shade): String {
    if (shade.mainsPowered) return "mains-powered"
    val percent = shade.batteryPercent ?: return "not read yet"
    val level = when (batteryLevelOf(percent)) {
        BatteryLevel.LOW -> "low"
        BatteryLevel.MEDIUM -> "medium"
        BatteryLevel.HIGH -> "high"
        // Outside 0..100: not a percentage, so do not pretend to grade it.
        BatteryLevel.UNKNOWN -> "unrecognised value"
    }
    val readAt = shade.batteryReadAt?.let { " at $it" } ?: ""
    return "$percent% ($level)$readAt"
}

/** Short battery string for a list row, where the timestamp is noise. */
internal fun batterySummary(shade: Shade): String {
    if (shade.mainsPowered) return "Mains"
    val percent = shade.batteryPercent ?: return "Battery not read"
    return "$percent%"
}

/**
 * The shade's rail/tilt positions, showing only the fields its capability says
 * it has — an unused byte decodes to *something*, and on a friendly screen that
 * would read as a real position.
 *
 * Deliberately reported as bare percentages rather than "open"/"closed".
 * `ShadeState.primaryPercent` documents 0 as fully open, and the capability
 * table marks some types as reporting primary inverted, but neither has been
 * confirmed against hardware for more than one shade (`docs/PROTOCOL.md` §8).
 * A number the user can compare against what they see is honest; a word that
 * might be backwards is not.
 */
internal fun positionSummary(shade: Shade): String {
    val state = shade.state ?: return "Position unknown"
    val capability = shade.capabilities?.capability

    val parts = buildList {
        if (capability == null || capability.hasPrimaryRail) {
            add("Primary ${percent(state.primaryPercent)}")
        }
        if (capability?.hasSecondaryRail == true) {
            add("Secondary ${percent(state.secondaryPercent)}")
        }
        if (capability?.hasTilt == true) {
            add("Tilt ${state.tiltPercent?.let { "$it%" } ?: "-"}")
        }
    }

    return if (parts.isEmpty()) "Position unknown" else parts.joinToString("   ")
}

/**
 * How recently the shade was heard from, as coarse relative text.
 *
 * "Never" here means never *in this process*, not never at all: advertisement
 * state is in-memory only, so a freshly launched app legitimately knows a saved
 * shade without having heard it yet. Saying "not seen yet" rather than
 * "offline" keeps that distinction, because the two call for different
 * reactions from the user.
 */
internal fun lastSeenText(shade: Shade, now: Instant = Instant.now()): String {
    val lastSeen = shade.lastSeenAt ?: return "Not seen yet"
    val seconds = Duration.between(lastSeen, now).seconds

    return when {
        seconds < 0 -> "In range" // clock moved backwards; not worth rendering as a negative age
        seconds < 10 -> "In range"
        seconds < 60 -> "Seen ${seconds}s ago"
        seconds < 3_600 -> "Seen ${seconds / 60}m ago"
        seconds < 86_400 -> "Seen ${seconds / 3_600}h ago"
        else -> "Seen ${seconds / 86_400}d ago"
    }
}

internal fun percent(value: Double?): String =
    value?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "-"

/**
 * Marks a field the shade's capability says it does not have, so a decoded
 * value from an unused byte is not read as a real position. Null capability
 * (nothing decoded yet) annotates nothing.
 */
internal fun unsupported(supported: Boolean?): String =
    if (supported == false) "  [not supported by capability]" else ""

/**
 * Rooms alphabetically, with unfiled shades last: [NO_ROOM_LABEL] is a
 * placeholder rather than a room name, so sorting it in among the real ones
 * would put it in an arbitrary and confusing position.
 */
internal fun groupIntoRooms(shades: List<Shade>): List<Pair<String, List<Shade>>> {
    val named = shades
        .filterNot { it.room.isNullOrBlank() }
        .groupBy { it.room.orEmpty().trim() }
        .toList()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { (room, _) -> room })
        .map { (room, roomShades) -> room to roomShades.sortedBy { it.label } }

    val unfiled = shades.filter { it.room.isNullOrBlank() }
    return if (unfiled.isEmpty()) named else named + (NO_ROOM_LABEL to unfiled.sortedBy { it.label })
}

/**
 * What to tell the user about one command's result.
 *
 * Two rules hold this together. A [CommandOutcome.NotAttempted] reason says
 * what to *do* — none of them is fixed by pressing the button again, so none
 * of them gets phrased as a failure to retry. And the write stage is described
 * as genuinely uncertain, because it is: the frame may have reached the shade
 * with only the acknowledgement lost, so claiming nothing happened would be a
 * guess, and on a device that physically moves it is the wrong guess to make
 * confidently.
 */
internal fun commandOutcomeText(outcome: CommandOutcome): String = when (outcome) {
    CommandOutcome.Sent -> "Sent."

    CommandOutcome.NotAttempted.NoKeystream ->
        "This home has no key yet, so nothing was sent. Controlling shades needs " +
            "keystream setup, which is not built yet."

    CommandOutcome.NotAttempted.NoHomeId ->
        "This shade has not been heard from yet, so the home it belongs to is " +
            "unknown and nothing was sent. Bring it in range and let it be scanned."

    CommandOutcome.NotAttempted.UnknownShade ->
        "This shade is not set up, so nothing was sent. Give it a name first."

    CommandOutcome.NotAttempted.MissingConnectPermission ->
        "Nothing was sent: the app is not allowed to connect to nearby devices. " +
            "Grant the Nearby devices permission in system settings."

    CommandOutcome.NotAttempted.BluetoothOff ->
        "Nothing was sent: Bluetooth is off."

    CommandOutcome.NotAttempted.MalformedAddress ->
        "Nothing was sent: this shade's stored address is not valid. Forget it and set it up again."

    is CommandOutcome.TransportFailed -> when (outcome.stage) {
        CommandOutcome.TransportFailed.Stage.CONNECT ->
            "Could not reach the shade, so it did not move. It may be out of range or asleep."

        CommandOutcome.TransportFailed.Stage.DISCOVER ->
            "Connected, but the shade did not report its controls, so it did not move. Try again."

        CommandOutcome.TransportFailed.Stage.WRITE ->
            "The command was not acknowledged. The shade may or may not have moved — " +
                "check it, or wait for its next position report."

        CommandOutcome.TransportFailed.Stage.PERMISSION_REVOKED ->
            "The Nearby devices permission was withdrawn mid-command, so the shade may or " +
                "may not have moved."
    }
}

/**
 * Whether a [CommandOutcome] is worth offering a retry for. Retrying a
 * [CommandOutcome.NotAttempted] cannot help — the user has something to fix
 * first — so the button would only invite them to hammer it.
 */
internal fun isWorthRetrying(outcome: CommandOutcome): Boolean =
    outcome is CommandOutcome.TransportFailed

/**
 * What to tell the user about a whole action's run.
 *
 * The case worth special-casing is an action where nothing was transmitted at
 * all — which, until keystream onboarding exists, is every action. Listing six
 * shades that each "failed" invites the user to go and investigate six shades,
 * when the single cause is that setup is unfinished. One sentence about the
 * cause beats six about its symptoms.
 */
internal fun actionResultText(
    result: ActionResult,
    labelForMacAddress: (String) -> String,
): String = when (result) {
    ActionResult.Pending -> "Running…"

    ActionResult.Success -> "Sent."

    is ActionResult.Failed -> when {
        result.nothingAttempted -> {
            // Every shade was blocked before BLE. If they were all blocked for
            // the same reason, that reason is the whole story.
            val reasons = result.outcomes.values.toSet()
            // commandOutcomeText already says nothing was sent, so when every
            // shade was blocked for the same reason it stands alone.
            reasons.singleOrNull()?.let(::commandOutcomeText)
                ?: "Nothing was sent — these shades are not ready to be controlled."
        }

        else -> {
            val names = result.failedMacAddresses.map(labelForMacAddress).sorted()
            "Some shades did not respond: ${names.joinToString(", ")}."
        }
    }
}

/**
 * How many shades an action would move, for the Quick Settings tile picker.
 *
 * A tile is one tap, reachable from the lock screen, so how much it does is
 * worth stating before it is chosen rather than after it is pressed.
 */
internal fun tileActionDescription(action: ShadeAction): String = when (action.commands.size) {
    0 -> "No shades — this action does nothing."
    1 -> "1 shade"
    else -> "${action.commands.size} shades"
}
