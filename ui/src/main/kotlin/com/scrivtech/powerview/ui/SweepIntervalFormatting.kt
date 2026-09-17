package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.SweepInterval

/**
 * Wording for the battery-sweep interval setting. Pure, so it is tested
 * off-device (`docs/HANDOFF.md`, "Verifying work without an Android SDK").
 *
 * Every option names its cost as well as its benefit. This setting trades
 * the shades' battery against how quickly a flat one is noticed, and a list
 * of bare intervals would hide the half of that which is counter-intuitive:
 * that checking more often is itself a drain.
 */
internal fun sweepIntervalLabel(interval: SweepInterval): String = when (interval) {
    SweepInterval.DAILY -> "Daily"
    SweepInterval.EVERY_THREE_DAYS -> "Every 3 days"
    SweepInterval.WEEKLY -> "Weekly"
    SweepInterval.FORTNIGHTLY -> "Every 2 weeks"
    SweepInterval.MONTHLY -> "Monthly"
    SweepInterval.OFF -> "Off"
}

internal fun sweepIntervalDescription(interval: SweepInterval): String = when (interval) {
    SweepInterval.DAILY ->
        "Quickest warning of a flat shade, and the most power spent checking."

    SweepInterval.EVERY_THREE_DAYS ->
        "A compromise if weekly has let a shade run flat on you."

    SweepInterval.WEEKLY ->
        "The default, and a good balance for most homes."

    SweepInterval.FORTNIGHTLY ->
        "Less checking. A shade could sit flat for up to two weeks first."

    SweepInterval.MONTHLY ->
        "Least power spent checking. A shade could sit flat for up to a month."

    SweepInterval.OFF ->
        "No automatic checking at all. Use Check now, or read a shade from " +
            "its own screen."
}

/** The explanation above the list — the trade the setting is actually making. */
internal const val SWEEP_INTERVAL_EXPLANATION: String =
    "Checking a battery means connecting to the shade, which uses a little of " +
        "the power it is measuring. Checking more often warns you sooner but " +
        "costs more."
