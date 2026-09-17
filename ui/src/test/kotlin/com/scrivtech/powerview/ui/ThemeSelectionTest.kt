package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSelectionTest {

    @Test
    fun `system mode follows the system setting`() {
        assertEquals(
            ThemeChoice(dark = false, trueBlack = false),
            resolveTheme(ThemeMode.SYSTEM, systemInDarkTheme = false),
        )
        assertEquals(
            ThemeChoice(dark = true, trueBlack = false),
            resolveTheme(ThemeMode.SYSTEM, systemInDarkTheme = true),
        )
    }

    @Test
    fun `explicit modes ignore the system setting`() {
        for (systemDark in listOf(false, true)) {
            assertEquals(
                "LIGHT with systemDark=$systemDark",
                ThemeChoice(dark = false, trueBlack = false),
                resolveTheme(ThemeMode.LIGHT, systemDark),
            )
            assertEquals(
                "DARK with systemDark=$systemDark",
                ThemeChoice(dark = true, trueBlack = false),
                resolveTheme(ThemeMode.DARK, systemDark),
            )
            assertEquals(
                "OLED with systemDark=$systemDark",
                ThemeChoice(dark = true, trueBlack = true),
                resolveTheme(ThemeMode.OLED, systemDark),
            )
        }
    }

    @Test
    fun `oled is a dark theme`() {
        // Anything asking "is this dark" — status bar icons, the dark scheme
        // itself — must treat OLED as dark. Only the palette cares that it is
        // also black.
        assertTrue(resolveTheme(ThemeMode.OLED, systemInDarkTheme = false).dark)
    }

    @Test
    fun `only oled is true black`() {
        val blackModes = ThemeMode.entries.filter { resolveTheme(it, true).trueBlack }
        assertEquals(listOf(ThemeMode.OLED), blackModes)
    }

    @Test
    fun `light is never dark whatever the system says`() {
        assertFalse(resolveTheme(ThemeMode.LIGHT, systemInDarkTheme = true).dark)
    }

    @Test
    fun `every mode has a distinct label and a description`() {
        val labels = ThemeMode.entries.map(::themeModeLabel)
        assertEquals(labels.size, labels.toSet().size)
        for (mode in ThemeMode.entries) {
            assertTrue("blank label for $mode", themeModeLabel(mode).isNotBlank())
            assertTrue("blank description for $mode", themeModeDescription(mode).isNotBlank())
        }
    }

    @Test
    fun `dark and oled are described differently`() {
        // They are the two options a user cannot tell apart from the name
        // alone, so the descriptions are what makes the choice meaningful.
        assertNotEquals(themeModeDescription(ThemeMode.DARK), themeModeDescription(ThemeMode.OLED))
    }
}
