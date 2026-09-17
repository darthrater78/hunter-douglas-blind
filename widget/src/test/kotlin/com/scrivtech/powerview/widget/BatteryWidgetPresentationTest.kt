package com.scrivtech.powerview.widget

import com.scrivtech.powerview.data.BatteryLevel
import com.scrivtech.powerview.data.ShadeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryWidgetPresentationTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun shade(
        mac: String,
        label: String = "Shade $mac",
        percent: Int? = 80,
        readDaysAgo: Long? = 0,
        mains: Boolean = false,
    ) = ShadeMetadata(
        macAddress = mac,
        label = label,
        batteryPercent = percent,
        batteryReadAtEpochMillis = readDaysAgo?.let { now - it * day },
        mainsPowered = mains,
    )

    @Test
    fun `mains-powered shades are left out entirely`() {
        // They are out of the sweep and out of alerting for the same reason;
        // a row that can never need attention costs a row that can.
        val rows = batteryRows(listOf(shade("a"), shade("b", mains = true)), now)
        assertEquals(listOf("a"), rows.map { it.macAddress })
    }

    @Test
    fun `a never-read shade is null, never zero`() {
        val row = batteryRows(listOf(shade("a", percent = null, readDaysAgo = null)), now).single()

        assertEquals(null, row.percent)
        assertEquals(BatteryLevel.UNKNOWN, row.level)
        assertEquals("Not read yet", batteryRowStatus(row))
        assertFalse("an unknown battery must not count as low", row.low)
    }

    @Test
    fun `never-read shades sort above even the flattest known one`() {
        val rows = batteryRows(
            listOf(
                shade("known-low", percent = 3),
                shade("unread", percent = null, readDaysAgo = null),
                shade("healthy", percent = 90),
            ),
            now,
        )
        assertEquals(listOf("unread", "known-low", "healthy"), rows.map { it.macAddress })
    }

    @Test
    fun `equal percentages fall back to label order`() {
        val rows = batteryRows(
            listOf(shade("b", label = "Zebra", percent = 50), shade("a", label = "Apple", percent = 50)),
            now,
        )
        assertEquals(listOf("Apple", "Zebra"), rows.map { it.label })
    }

    @Test
    fun `a reading is only called stale after two missed sweeps`() {
        val fresh = batteryRows(listOf(shade("a", readDaysAgo = STALE_READING_DAYS - 1)), now).single()
        val stale = batteryRows(listOf(shade("a", readDaysAgo = STALE_READING_DAYS)), now).single()

        assertFalse(fresh.stale)
        assertTrue(stale.stale)
        assertEquals("80%", batteryRowStatus(fresh))
        assertEquals("80% · 14d old", batteryRowStatus(stale))
    }

    @Test
    fun `a clock that moved backwards reads as fresh, not as a future reading`() {
        val row = batteryRows(listOf(shade("a", readDaysAgo = -30)), now).single()

        assertEquals(0L, row.ageDays)
        assertFalse(row.stale)
    }

    @Test
    fun `the low threshold is inclusive and matches the notifier's`() {
        val atThreshold = batteryRows(listOf(shade("a", percent = 20)), now).single()
        val above = batteryRows(listOf(shade("a", percent = 21)), now).single()

        assertTrue(atThreshold.low)
        assertEquals(BatteryLevel.LOW, atThreshold.level)
        assertFalse(above.low)
    }

    @Test
    fun `the summary counts low and unread separately`() {
        // Folding them together would either overstate what is low or stay
        // silent while shades have never been read at all.
        val rows = batteryRows(
            listOf(
                shade("a", percent = 5),
                shade("b", percent = null, readDaysAgo = null),
                shade("c", percent = 90),
            ),
            now,
        )
        assertEquals("1 low, 1 not read of 3", batterySummaryLine(rows))
    }

    @Test
    fun `an all-healthy summary states the threshold it is judging against`() {
        val rows = batteryRows(listOf(shade("a", percent = 90), shade("b", percent = 55)), now)
        assertEquals("All 2 above 20%", batterySummaryLine(rows))
    }

    @Test
    fun `no shades is said plainly rather than as zero of zero`() {
        assertEquals("No battery shades set up", batterySummaryLine(emptyList()))
        assertEquals(emptyList<BatteryRow>(), batteryRows(emptyList(), now))
    }

    @Test
    fun `only-low and only-unread summaries read correctly`() {
        val onlyLow = batteryRows(listOf(shade("a", percent = 5)), now)
        assertEquals("1 low of 1", batterySummaryLine(onlyLow))

        val onlyUnread = batteryRows(listOf(shade("a", percent = null, readDaysAgo = null)), now)
        assertEquals("1 not read of 1", batterySummaryLine(onlyUnread))
    }
}

class BatteryRowsToShowTest {

    private fun rows(n: Int) = (1..n).map {
        BatteryRow(macAddress = "m$it", label = "Shade $it", percent = it, level = com.scrivtech.powerview.data.BatteryLevel.LOW, ageDays = 0)
    }

    @Test
    fun `everything fits when there are few enough`() {
        val shown = batteryRowsToShow(rows(3))
        assertEquals(3, shown.rows.size)
        assertEquals(0, shown.hidden)
        assertEquals(null, hiddenRowsText(shown.hidden))
    }

    @Test
    fun `the overflow is counted, not silently dropped`() {
        // Rows are sorted worst-first, so the ones cut are the least urgent —
        // but a monitoring widget that hid them without saying so would be
        // lying by omission.
        val shown = batteryRowsToShow(rows(12))
        assertEquals(MAX_BATTERY_ROWS, shown.rows.size)
        assertEquals(12 - MAX_BATTERY_ROWS, shown.hidden)
        assertEquals("7 more — open the app", hiddenRowsText(shown.hidden))
    }

    @Test
    fun `exactly at the cap shows no overflow line`() {
        val shown = batteryRowsToShow(rows(MAX_BATTERY_ROWS))
        assertEquals(0, shown.hidden)
        assertEquals(null, hiddenRowsText(shown.hidden))
    }

    @Test
    fun `one hidden row is singular`() {
        assertEquals("1 more — open the app", hiddenRowsText(1))
    }

    @Test
    fun `the worst rows are the ones kept`() {
        val shown = batteryRowsToShow(rows(8), max = 2)
        assertEquals(listOf("Shade 1", "Shade 2"), shown.rows.map { it.label })
    }

    @Test
    fun `a zero cap hides everything and says so rather than throwing`() {
        val shown = batteryRowsToShow(rows(3), max = 0)
        assertEquals(emptyList<BatteryRow>(), shown.rows)
        assertEquals(3, shown.hidden)
    }
}
