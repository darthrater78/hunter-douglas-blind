package com.scrivtech.powerview.widget

import com.scrivtech.powerview.data.ActionIcon
import com.scrivtech.powerview.data.ShadeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresentationTest {

    private fun action(id: String, label: String = "Action $id") =
        ShadeAction(id = id, label = label, icon = ActionIcon.CUSTOM, commands = emptyList())

    private fun contents(vararg slots: WidgetSlot) = WidgetContents(slots.toList())

    @Test
    fun `encode then decode round-trips`() {
        val original = contents(
            WidgetSlot("a", SlotRun.IDLE),
            WidgetSlot("b", SlotRun.PENDING),
            WidgetSlot("c", SlotRun.FAILED),
        )
        assertEquals(original, decodeWidgetContents(encodeWidgetContents(original)))
    }

    @Test
    fun `absent or empty state decodes to no slots`() {
        assertEquals(WidgetContents(), decodeWidgetContents(null))
        assertEquals(WidgetContents(), decodeWidgetContents(""))
    }

    @Test
    fun `a malformed line is dropped rather than throwing`() {
        // This runs inside a Glance state update with no UI to report to, so
        // the failure mode has to be a missing button, not a crash on the
        // user's home screen.
        val decoded = decodeWidgetContents("IDLE|keep\nrubbish\n|no-run-name\nFAILED|\nPENDING|also-keep")
        assertEquals(
            listOf(WidgetSlot("keep", SlotRun.IDLE), WidgetSlot("also-keep", SlotRun.PENDING)),
            decoded.slots,
        )
    }

    @Test
    fun `an unknown run state decodes as idle`() {
        assertEquals(
            listOf(WidgetSlot("a", SlotRun.IDLE)),
            decodeWidgetContents("SOMETHING_NEW|a").slots,
        )
    }

    @Test
    fun `decoding caps at the maximum`() {
        val raw = (1..20).joinToString("\n") { "IDLE|$it" }
        assertEquals(MAX_WIDGET_ACTIONS, decodeWidgetContents(raw).slots.size)
    }

    @Test
    fun `configure keeps run state for surviving actions`() {
        val previous = contents(
            WidgetSlot("a", SlotRun.FAILED),
            WidgetSlot("b", SlotRun.PENDING),
        )
        val next = configureSlots(previous, listOf("b", "c"))

        assertEquals(
            listOf(WidgetSlot("b", SlotRun.PENDING), WidgetSlot("c", SlotRun.IDLE)),
            next.slots,
        )
    }

    @Test
    fun `configure drops duplicates blanks and anything past the maximum`() {
        val ids = listOf("a", "a", "", "b") + (1..10).map { "extra$it" }
        val slots = configureSlots(WidgetContents(), ids).slots

        assertEquals(MAX_WIDGET_ACTIONS, slots.size)
        assertEquals(listOf("a", "b", "extra1", "extra2", "extra3", "extra4"), slots.map { it.actionId })
    }

    @Test
    fun `withRunState touches only the named action`() {
        val before = contents(WidgetSlot("a", SlotRun.IDLE), WidgetSlot("b", SlotRun.IDLE))
        val after = withRunState(before, "b", SlotRun.PENDING)

        assertEquals(listOf(SlotRun.IDLE, SlotRun.PENDING), after.slots.map { it.run })
    }

    @Test
    fun `withRunState on an absent action changes nothing`() {
        val before = contents(WidgetSlot("a", SlotRun.IDLE))
        assertEquals(before, withRunState(before, "not-here", SlotRun.FAILED))
    }

    @Test
    fun `pending is the tap debounce`() {
        val c = contents(WidgetSlot("a", SlotRun.PENDING), WidgetSlot("b", SlotRun.FAILED))
        assertTrue(isPending(c, "a"))
        assertFalse(isPending(c, "b"))
        assertFalse("an action this widget does not show is not pending", isPending(c, "c"))
    }

    @Test
    fun `shows reports whether a status update concerns this widget`() {
        val c = contents(WidgetSlot("a"))
        assertTrue(shows(c, "a"))
        assertFalse(shows(c, "b"))
    }

    @Test
    fun `grid columns never leave a one-wide column it could avoid`() {
        assertEquals(1, gridColumns(1))
        assertEquals(2, gridColumns(2))
        assertEquals(3, gridColumns(3))
        // 2x2 rather than 4x1: a widget is wider than it is tall.
        assertEquals(2, gridColumns(4))
        assertEquals(3, gridColumns(5))
        assertEquals(3, gridColumns(6))
    }

    @Test
    fun `grid rows are never ragged by more than one cell`() {
        for (count in 1..MAX_WIDGET_ACTIONS) {
            val slots = (1..count).map { WidgetSlot("id$it") }
            val rows = gridRows(slots)

            assertEquals("every slot placed for count=$count", count, rows.sumOf { it.size })
            val widest = rows.maxOf { it.size }
            val narrowest = rows.minOf { it.size }
            assertTrue("ragged grid for count=$count: $rows", widest - narrowest <= 1)
        }
    }

    @Test
    fun `no rows for no slots`() {
        assertEquals(emptyList<List<WidgetSlot>>(), gridRows(emptyList()))
    }

    @Test
    fun `a deleted action is named rather than left blank`() {
        assertEquals("Open all", slotTitle(action("a", "Open all")))
        assertEquals("Deleted action", slotTitle(null))
    }

    @Test
    fun `only a finished failure and an in-flight run have a status line`() {
        assertNull(slotStatus(SlotRun.IDLE))
        assertEquals("Sending…", slotStatus(SlotRun.PENDING))
        assertTrue(slotStatus(SlotRun.FAILED).orEmpty().startsWith("Failed"))
    }

    @Test
    fun `a slot is tappable only when it has an action and is not already running`() {
        assertTrue(isSlotEnabled(WidgetSlot("a", SlotRun.IDLE), action("a")))
        assertTrue(isSlotEnabled(WidgetSlot("a", SlotRun.FAILED), action("a")))
        assertFalse(isSlotEnabled(WidgetSlot("a", SlotRun.PENDING), action("a")))
        assertFalse(isSlotEnabled(WidgetSlot("a", SlotRun.IDLE), null))
    }
}

class TilePresentationTest {

    @Test
    fun `an unconfigured tile points at the app whatever the run state`() {
        // A deleted action and a never-chosen one are the same thing here:
        // nothing to run, and the same trip into the app to fix it.
        for (run in SlotRun.entries) {
            assertEquals(
                "run=$run",
                "Choose an action in the app",
                tileSubtitle(hasAction = false, run = run),
            )
        }
    }

    @Test
    fun `a configured tile reports its last run`() {
        assertEquals("Tap to run", tileSubtitle(hasAction = true, run = SlotRun.IDLE))
        assertEquals("Sending…", tileSubtitle(hasAction = true, run = SlotRun.PENDING))
        assertEquals("Last run failed", tileSubtitle(hasAction = true, run = SlotRun.FAILED))
    }

    @Test
    fun `every subtitle is non-blank`() {
        for (hasAction in listOf(false, true)) {
            for (run in SlotRun.entries) {
                assertTrue(tileSubtitle(hasAction, run).isNotBlank())
            }
        }
    }
}
