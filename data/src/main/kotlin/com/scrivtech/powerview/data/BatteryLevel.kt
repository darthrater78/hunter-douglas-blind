package com.scrivtech.powerview.data

/**
 * Battery grading, split out of `BatteryReader.kt` because that file imports
 * Android and this does not.
 *
 * That is not tidiness: it is what lets these be compiled and tested in the
 * isolated harness (`docs/HANDOFF.md`, "Verifying work without an Android
 * SDK") instead of being copied into it by hand, which is how a copy drifts
 * from the original and a test starts proving something about neither.
 */

/**
 * A coarse reading of the battery percentage, for surfaces that want a state
 * rather than a number. A value outside 0..100 is [UNKNOWN] rather than
 * clamped, so a firmware change that starts reporting something other than a
 * percentage is visible instead of silently mislabelled.
 */
public enum class BatteryLevel { LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * Maps a battery percentage to its [BatteryLevel]. [LOW_BATTERY_PERCENT] is
 * the threshold the step-10 sweep will alert on, so it is defined once here
 * rather than being picked again in the notification code.
 */
public fun batteryLevelOf(percent: Int?): BatteryLevel = when (percent) {
    null -> BatteryLevel.UNKNOWN
    in 0..LOW_BATTERY_PERCENT -> BatteryLevel.LOW
    in (LOW_BATTERY_PERCENT + 1)..60 -> BatteryLevel.MEDIUM
    in 61..100 -> BatteryLevel.HIGH
    else -> BatteryLevel.UNKNOWN
}

/** At or below this percentage a shade is considered low (spec §2.5). */
public const val LOW_BATTERY_PERCENT: Int = 20
