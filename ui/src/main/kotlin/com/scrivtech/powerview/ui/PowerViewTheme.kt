package com.scrivtech.powerview.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.scrivtech.powerview.data.ThemeMode

/**
 * Light and dark stay on Material 3's baseline palette. The app has no brand
 * colours, and inventing some in the change that adds a theme picker would
 * mean every screen's appearance changed for reasons unrelated to the setting
 * the user just used.
 */
private val LightScheme = lightColorScheme()
private val DarkScheme = darkColorScheme()

/**
 * The OLED scheme: [DarkScheme] with the whole background/surface family
 * pulled down to black.
 *
 * Two details here are the difference between "OLED theme" and "unreadable":
 *
 * **The containers are not black.** Material draws cards, sheets and the app
 * bar from the `surfaceContainer` roles, and a black card on a black
 * background is an invisible card — the user loses every boundary on the
 * screen. So the background is a true `0xFF000000` (which is what actually
 * switches OLED pixels off, and where most of a screen's area is), and the
 * containers sit on a near-black ramp just bright enough to read as edges.
 *
 * **`surfaceTint` is black.** Material blends `surfaceTint` into a surface in
 * proportion to its elevation, so a nominally black elevated surface would
 * come out grey and the theme would quietly fail at the exact components it
 * most needs to work on. Blending black into black is a no-op, which is the
 * intent.
 */
private val OledScheme = DarkScheme.copy(
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF242424),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1D1D1D),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceVariant = Color(0xFF1D1D1D),
    surfaceTint = Color.Black,
    scrim = Color.Black,
)

/**
 * Wraps the app in the scheme [mode] asks for. Hosts call this instead of
 * `MaterialTheme` directly so there is one place that knows what each
 * [ThemeMode] looks like.
 */
@Composable
public fun PowerViewTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val choice = resolveTheme(mode, isSystemInDarkTheme())
    val scheme = when {
        choice.trueBlack -> OledScheme
        choice.dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
