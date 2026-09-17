package com.scrivtech.powerview.data

/**
 * How often [BatterySweepWorker] reads every battery-powered shade.
 *
 * This is a setting rather than a constant because the right answer depends
 * on the home, and the cost of being wrong runs both ways. Reading a battery
 * means connecting to the shade, which spends the power being measured — so
 * sweeping daily across a dozen shades is itself a drain. But a month between
 * sweeps means a shade can die two weeks before anything says so.
 *
 * [OFF] is a real choice, not a footgun: someone whose shades are mostly
 * mains-powered, or who would rather read batteries by hand from the app,
 * should not have to tolerate a background job to get the rest of the app.
 *
 * No Android imports, so the logic built on it stays testable off-device —
 * see `docs/HANDOFF.md`, "Verifying work without an Android SDK".
 */
public enum class SweepInterval(
    /** Days between sweeps, or null when the sweep does not run at all. */
    public val days: Long?,
) {
    DAILY(1),
    EVERY_THREE_DAYS(3),
    WEEKLY(7),
    FORTNIGHTLY(14),
    MONTHLY(30),
    OFF(null),
    ;

    public companion object {
        /**
         * Weekly, which is what the sweep did before it was configurable.
         * An existing install that never touches this setting keeps exactly
         * the behaviour it had.
         */
        public val DEFAULT: SweepInterval = WEEKLY
    }
}
