package com.scrivtech.powerview.data

/**
 * Which colour scheme the app should use, as chosen by the user and persisted
 * by [SettingsStore].
 *
 * Deliberately has no Android imports so the resolution logic that consumes it
 * ( `:ui`'s `ThemeSelection.kt` ) stays unit-testable off-device — see
 * `docs/HANDOFF.md`, "Verifying work without an Android SDK".
 */
public enum class ThemeMode {
    /** Follow the system light/dark setting. The default. */
    SYSTEM,

    LIGHT,

    /** Material's standard dark scheme: dark greys, not black. */
    DARK,

    /**
     * Dark, with pure black backgrounds. On an OLED panel a black pixel is an
     * unlit pixel, so this genuinely saves power and removes the grey glow in
     * a dark room — which is the whole point of asking for it, and the reason
     * it is a separate choice from [DARK] rather than a redefinition of it.
     */
    OLED,
}
