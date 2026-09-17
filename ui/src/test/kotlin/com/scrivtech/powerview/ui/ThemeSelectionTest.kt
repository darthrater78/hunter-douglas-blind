package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.ActionIcon
import com.scrivtech.powerview.data.Command
import com.scrivtech.powerview.data.ShadeAction
import com.scrivtech.powerview.data.SweepInterval
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

class TileActionDescriptionTest {

    private fun actionWith(commandCount: Int) = ShadeAction(
        id = "a",
        label = "Action",
        icon = ActionIcon.CUSTOM,
        commands = (1..commandCount).map { Command(macAddress = "AA:BB:CC:DD:EE:0$it") },
    )

    @Test
    fun `singular and plural are not fudged`() {
        assertEquals("1 shade", tileActionDescription(actionWith(1)))
        assertEquals("2 shades", tileActionDescription(actionWith(2)))
        assertEquals("6 shades", tileActionDescription(actionWith(6)))
    }

    @Test
    fun `an empty action says it does nothing rather than reading as zero shades`() {
        // It can happen: the editor drops shades with no fields enabled.
        assertEquals("No shades — this action does nothing.", tileActionDescription(actionWith(0)))
    }
}

class SweepIntervalFormattingTest {

    @Test
    fun `every interval has a distinct label and a description`() {
        val labels = SweepInterval.entries.map(::sweepIntervalLabel)
        assertEquals(labels.size, labels.toSet().size)

        val descriptions = SweepInterval.entries.map(::sweepIntervalDescription)
        assertEquals(descriptions.size, descriptions.toSet().size)

        for (interval in SweepInterval.entries) {
            assertTrue("blank label for $interval", sweepIntervalLabel(interval).isNotBlank())
            assertTrue("blank description for $interval", sweepIntervalDescription(interval).isNotBlank())
        }
    }

    @Test
    fun `off explains how to get a reading instead of just saying no`() {
        // Off must not read as "never see a battery level again".
        assertTrue(sweepIntervalDescription(SweepInterval.OFF).contains("Check now"))
    }

    @Test
    fun `the explanation names the cost, not just the benefit`() {
        // The counter-intuitive half of this setting is that checking more
        // often is itself a drain; if the screen does not say so, the setting
        // looks like free safety.
        assertTrue(SWEEP_INTERVAL_EXPLANATION.contains("power"))
    }
}
