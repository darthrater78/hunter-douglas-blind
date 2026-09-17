package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.ThemeMode

/**
 * Which of the three colour schemes a [ThemeMode] resolves to, once the
 * system's own light/dark setting is known.
 *
 * [trueBlack] is a separate flag rather than a third value of [dark] because
 * every caller that cares about dark-versus-light — status bar icons, whether
 * to use the dark scheme at all — wants OLED treated as dark. Only the
 * palette itself cares about the distinction.
 */
internal data class ThemeChoice(val dark: Boolean, val trueBlack: Boolean)

/**
 * Pure so it can be tested off-device (`docs/HANDOFF.md`, "Verifying work
 * without an Android SDK"): [systemInDarkTheme] is passed in rather than read
 * from Compose here.
 *
 * Note that OLED does *not* consult the system setting. Someone who picks it
 * has asked for black, and silently going light during the day would be a
 * different theme than the one they chose — [ThemeMode.SYSTEM] already exists
 * for people who want that.
 */
internal fun resolveTheme(mode: ThemeMode, systemInDarkTheme: Boolean): ThemeChoice = when (mode) {
    ThemeMode.SYSTEM -> ThemeChoice(dark = systemInDarkTheme, trueBlack = false)
    ThemeMode.LIGHT -> ThemeChoice(dark = false, trueBlack = false)
    ThemeMode.DARK -> ThemeChoice(dark = true, trueBlack = false)
    ThemeMode.OLED -> ThemeChoice(dark = true, trueBlack = true)
}

internal fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.OLED -> "Black (OLED)"
}

internal fun themeModeDescription(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Light or dark, whichever the phone is set to."
    ThemeMode.LIGHT -> "Always light, whatever the phone is set to."
    ThemeMode.DARK -> "Always dark. Dark greys, the standard Material scheme."
    ThemeMode.OLED ->
        "Always dark, with pure black backgrounds. On an OLED screen black " +
            "pixels are switched off, so this saves power and avoids the grey " +
            "glow in a dark room."
}
