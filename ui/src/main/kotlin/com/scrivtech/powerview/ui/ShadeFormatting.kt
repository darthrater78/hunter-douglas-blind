package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.BatteryLevel
import com.scrivtech.powerview.data.Shade
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
