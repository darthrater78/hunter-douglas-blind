package com.scrivtech.powerview.widget

import com.scrivtech.powerview.data.BatteryLevel
import com.scrivtech.powerview.data.LOW_BATTERY_PERCENT
import com.scrivtech.powerview.data.ShadeMetadata
import com.scrivtech.powerview.data.batteryLevelOf

/**
 * Everything the battery widget shows, decided without Android so it can be
 * tested off-device (`docs/HANDOFF.md`, "Verifying work without an Android
 * SDK").
 *
 * The widget's whole job is answering "does anything need new batteries?" at
 * a glance, and the two ways to get that wrong are both about claiming to
 * know more than the app does. A shade that has never been read is not at
 * 0%, and a reading taken a month ago is not a reading of today — so
 * [BatteryRow] carries both cases explicitly rather than letting either
 * render as a bare number.
 */

/**
 * A reading older than this is shown with its age attached.
 *
 * Two sweep periods. `BatterySweepWorker` runs weekly, so one missed sweep
 * is ordinary — the phone was away from the shades, or the battery-not-low
 * constraint deferred it. Two means the number on screen is not being
 * maintained, and the user should know that before trusting it.
 */
internal const val STALE_READING_DAYS: Long = 14

private const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000

/** One shade's line in the battery widget. */
internal data class BatteryRow(
    val macAddress: String,
    val label: String,
    /** Null when this shade has never been read — not zero. */
    val percent: Int?,
    val level: BatteryLevel,
    /** Whole days since the reading, or null if it has never been read. */
    val ageDays: Long?,
) {
    val stale: Boolean get() = ageDays != null && ageDays >= STALE_READING_DAYS
    val low: Boolean get() = percent != null && percent <= LOW_BATTERY_PERCENT
}

/**
 * The shades worth showing, worst first.
 *
 * Mains-powered shades are left out entirely rather than listed as "Mains".
 * They are excluded from the sweep and from alerting for the same reason,
 * and a widget has little room — a row that can never need attention is a
 * row taken from one that can.
 *
 * Never-read shades sort above everything. An unknown battery is at least as
 * worth attention as a known-low one: it is the shade the sweep has not
 * managed to reach, so it is the one whose state the app is least entitled
 * to reassure anybody about.
 */
internal fun batteryRows(
    shades: Collection<ShadeMetadata>,
    nowEpochMillis: Long,
): List<BatteryRow> =
    shades
        .filterNot { it.mainsPowered }
        .map { shade ->
            BatteryRow(
                macAddress = shade.macAddress,
                label = shade.label,
                percent = shade.batteryPercent,
                level = batteryLevelOf(shade.batteryPercent),
                ageDays = shade.batteryReadAtEpochMillis?.let { readAt ->
                    // Negative ages happen: a clock moved backwards, or a
                    // restored backup. Report them as fresh rather than as a
                    // reading from the future.
                    ((nowEpochMillis - readAt) / MILLIS_PER_DAY).coerceAtLeast(0)
                },
            )
        }
        .sortedWith(
            compareBy<BatteryRow> { it.percent ?: Int.MIN_VALUE }
                .thenBy { it.label.lowercase() },
        )

/**
 * The right-hand side of a row: the number, or the reason there is not one.
 *
 * The age is attached only once it is [STALE_READING_DAYS] old. Putting it on
 * every row would bury the one case it matters for in noise, and the point of
 * saying it at all is that *this* number should not be trusted.
 */
internal fun batteryRowStatus(row: BatteryRow): String = when {
    row.percent == null -> "Not read yet"
    row.stale -> "${row.percent}% · ${row.ageDays}d old"
    else -> "${row.percent}%"
}

/**
 * The header line: what the user came to the widget to find out.
 *
 * Low shades are counted first because that is the actionable number.
 * Unread ones are called out separately rather than folded in — "2 low" when
 * one of them is merely unknown would be wrong, and a widget saying nothing
 * is low while three shades have never been read would be worse.
 */
internal fun batterySummaryLine(rows: List<BatteryRow>): String {
    if (rows.isEmpty()) return "No battery shades set up"

    val low = rows.count { it.low }
    val unread = rows.count { it.percent == null }

    val parts = buildList {
        if (low > 0) add("$low low")
        if (unread > 0) add("$unread not read")
    }

    return when {
        parts.isEmpty() -> "All ${rows.size} above $LOW_BATTERY_PERCENT%"
        else -> parts.joinToString(", ") + " of ${rows.size}"
    }
}

/**
 * How many rows the widget draws.
 *
 * Deliberately a cap with an overflow line rather than a scrolling list.
 * Glance has a lazy list, but this project pins Glance 1.1.1 and cannot
 * compile against it here to find out which of its signatures that version
 * has — and a widget is a glance, not a screen. With rows sorted worst
 * first, the ones that fit are the ones that matter, and the overflow line
 * points at the app for the rest.
 */
internal const val MAX_BATTERY_ROWS: Int = 5

/** The rows to draw, and how many were left out. */
internal data class BatteryRowsToShow(val rows: List<BatteryRow>, val hidden: Int)

/**
 * Truncates to [MAX_BATTERY_ROWS], reporting how many were dropped so the
 * widget can say so. Silently showing the first five of twelve would make a
 * monitoring widget lie by omission — the twelfth shade is exactly the one
 * someone would want to know about.
 */
internal fun batteryRowsToShow(rows: List<BatteryRow>, max: Int = MAX_BATTERY_ROWS): BatteryRowsToShow {
    val limit = max.coerceAtLeast(0)
    return BatteryRowsToShow(rows = rows.take(limit), hidden = (rows.size - limit).coerceAtLeast(0))
}

/** The overflow line, or null when everything fits. */
internal fun hiddenRowsText(hidden: Int): String? = when {
    hidden <= 0 -> null
    hidden == 1 -> "1 more — open the app"
    else -> "$hidden more — open the app"
}

/** Shown when the widget has nothing to list at all. */
internal const val NO_BATTERY_SHADES_TEXT: String =
    "No battery-powered shades yet. Name a shade in the app to track it."
