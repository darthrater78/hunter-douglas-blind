package com.scrivtech.powerview.widget

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryWidgetSizeTest {

    @Test
    fun `smallest declared size keeps room for the overflow line`() {
        // 110dp leaves 56dp: two rows would fit alone, but not two rows plus
        // the "N more" line, so one row is shown and the line stays visible.
        assertEquals(2, maxRowsForHeight(110.dp, totalRows = 2))
        assertEquals(1, maxRowsForHeight(110.dp, totalRows = 5))
    }

    @Test
    fun `everything that fits is shown without reserving overflow room`() {
        assertEquals(5, maxRowsForHeight(180.dp, totalRows = 5))
    }

    @Test
    fun `overflow room is reserved only when rows are actually hidden`() {
        assertEquals(4, maxRowsForHeight(180.dp, totalRows = 6))
    }

    @Test
    fun `a taller widget shows more rows`() {
        assertEquals(7, maxRowsForHeight(250.dp, totalRows = 20))
    }

    @Test
    fun `never fewer than one row, however small`() {
        assertEquals(1, maxRowsForHeight(0.dp, totalRows = 3))
    }

    @Test
    fun `capped at twice the old fixed limit on a very tall widget`() {
        assertEquals(MAX_BATTERY_ROWS * 2, maxRowsForHeight(2000.dp, totalRows = 50))
    }
}
