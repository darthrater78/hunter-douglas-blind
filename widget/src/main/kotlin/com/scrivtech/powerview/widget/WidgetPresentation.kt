package com.scrivtech.powerview.widget

import com.scrivtech.powerview.data.ShadeAction

/**
 * Everything about a widget's contents that is decidable without Android:
 * what a widget instance points at, what happened to each of those actions
 * last, how it is laid out, and what the words on it say.
 *
 * Kept free of Android imports on purpose, so it compiles and runs in the
 * isolated harness described in `docs/HANDOFF.md` ("Verifying work without an
 * Android SDK"). That matters more here than elsewhere: this container has no
 * Android SDK and no Glance to compile against, so the Glance code in
 * `ShadeActionWidget.kt` is checked for the first time by CI. Anything that
 * can be pulled out of it and tested here is a round trip saved.
 */

/**
 * How many actions one widget instance can hold — the 4x2 grid's capacity.
 * Past this the buttons are too small to hit reliably.
 */
internal const val MAX_WIDGET_ACTIONS: Int = 6

/** What happened to one of a widget's actions the last time it was tapped. */
internal enum class SlotRun {
    /** Never run, or ran and succeeded. Both draw as a plain button. */
    IDLE,

    /** Enqueued or running. Taps are ignored until it settles. */
    PENDING,

    /** The last run reported at least one shade that did not respond. */
    FAILED,
}

/** One action button on a widget. */
internal data class WidgetSlot(val actionId: String, val run: SlotRun = SlotRun.IDLE)

/** The whole of one widget instance's persisted state. */
internal data class WidgetContents(val slots: List<WidgetSlot> = emptyList())

private const val FIELD_SEPARATOR = '|'

/**
 * Serialised as one line per slot, `RUN|actionId`.
 *
 * Not JSON, deliberately: this is read and rewritten on every tap, from a
 * Glance state update that has to finish quickly, and the action ids are
 * UUIDs — so neither separator can occur in the data, and a hand-rolled
 * format costs nothing while a serializer dependency would be new surface in
 * a module that has none.
 */
internal fun encodeWidgetContents(contents: WidgetContents): String =
    contents.slots.joinToString("\n") { "${it.run.name}$FIELD_SEPARATOR${it.actionId}" }

/**
 * Decodes defensively: a malformed line is dropped and an unrecognised run
 * state reads as [SlotRun.IDLE], rather than either throwing.
 *
 * The reason is where this runs. A parse failure here happens inside a Glance
 * state update or a broadcast receiver, where there is no UI to report it and
 * an exception is a crash on the user's home screen. Losing a run *indicator*
 * is a far smaller harm than that, and the next tap restores it.
 */
internal fun decodeWidgetContents(raw: String?): WidgetContents {
    if (raw.isNullOrEmpty()) return WidgetContents()

    val slots = raw.lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf(FIELD_SEPARATOR)
            // Needs a non-empty run name before it and a non-empty id after.
            if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
            val run = SlotRun.entries.firstOrNull { it.name == line.substring(0, separator) }
                ?: SlotRun.IDLE
            WidgetSlot(actionId = line.substring(separator + 1), run = run)
        }
        .take(MAX_WIDGET_ACTIONS)
        .toList()

    return WidgetContents(slots)
}

/**
 * Applies a configuration change, keeping the run state of any action that
 * survives it.
 *
 * Keeping it matters for the reconfigure case: adding a seventh action to a
 * widget should not make five unrelated buttons forget that they failed a
 * moment ago.
 */
internal fun configureSlots(previous: WidgetContents, actionIds: List<String>): WidgetContents {
    val previousRuns = previous.slots.associate { it.actionId to it.run }
    return WidgetContents(
        actionIds
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_WIDGET_ACTIONS)
            .map { WidgetSlot(it, previousRuns[it] ?: SlotRun.IDLE) },
    )
}

/** Sets [actionId]'s run state, leaving every other slot alone. */
internal fun withRunState(contents: WidgetContents, actionId: String, run: SlotRun): WidgetContents =
    WidgetContents(contents.slots.map { if (it.actionId == actionId) it.copy(run = run) else it })

/** True when this widget already has [actionId] in flight — the tap debounce. */
internal fun isPending(contents: WidgetContents, actionId: String): Boolean =
    contents.slots.any { it.actionId == actionId && it.run == SlotRun.PENDING }

/** True when this widget shows [actionId] at all, so a status update concerns it. */
internal fun shows(contents: WidgetContents, actionId: String): Boolean =
    contents.slots.any { it.actionId == actionId }

/**
 * Column count for a given number of buttons. Chosen so a grid is never
 * ragged by more than one cell, and never one column wide when it could be
 * two: 4 reads far better as 2x2 than as 4x1 in a widget's aspect ratio.
 */
internal fun gridColumns(slotCount: Int): Int = when {
    slotCount <= 1 -> 1
    slotCount <= 3 -> slotCount
    slotCount == 4 -> 2
    else -> 3
}

/** The buttons arranged into rows, for a layout with no grid primitive. */
internal fun gridRows(slots: List<WidgetSlot>): List<List<WidgetSlot>> =
    if (slots.isEmpty()) emptyList() else slots.chunked(gridColumns(slots.size))

/** Shown when a widget instance points at nothing — reachable only by reconfiguring to empty. */
internal const val UNCONFIGURED_TEXT: String = "No action chosen. Long-press the widget to set it up."

/**
 * The button's caption. A slot whose action has since been deleted says so
 * rather than going blank: a blank button that silently does nothing is a bug
 * report, where a named one tells the user to reconfigure.
 */
internal fun slotTitle(action: ShadeAction?): String = action?.label ?: "Deleted action"

/**
 * The line under the caption, or null when there is nothing to say.
 *
 * "Failed" is as much as a home-screen button can honestly report. The
 * detailed `CommandOutcome` wording — including the load-bearing "may or may
 * not have moved" for a lost acknowledgement — needs room the widget does not
 * have, so the in-app screens carry that and this points at them.
 */
internal fun slotStatus(run: SlotRun): String? = when (run) {
    SlotRun.IDLE -> null
    SlotRun.PENDING -> "Sending…"
    SlotRun.FAILED -> "Failed — open the app"
}

/** True when a tap on this slot should do nothing. */
internal fun isSlotEnabled(slot: WidgetSlot, action: ShadeAction?): Boolean =
    action != null && slot.run != SlotRun.PENDING

// --- Quick Settings tile (build order step 11) -----------------------------
//
// The tile shares this file rather than having its own because it shares the
// vocabulary: one action, one run state, the same three outcomes. What it
// does not share is the widget's per-instance storage — a tile is a
// singleton, so its run state is held in memory and the wording below is the
// only part worth testing.

/** Shown as the tile's label when it has no action to name. */
internal const val TILE_UNCONFIGURED_LABEL: String = "PowerView"

/**
 * The tile's second line (API 29+).
 *
 * [hasAction] is false both when nothing has been chosen and when the chosen
 * action has since been deleted. Those are the same thing from the tile's
 * point of view — there is nothing to run — and the fix for both is the same
 * trip into the app, so they read the same.
 */
internal fun tileSubtitle(hasAction: Boolean, run: SlotRun): String = when {
    !hasAction -> "Choose an action in the app"
    run == SlotRun.PENDING -> "Sending…"
    run == SlotRun.FAILED -> "Last run failed"
    else -> "Tap to run"
}
