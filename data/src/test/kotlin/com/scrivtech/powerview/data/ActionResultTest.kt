package com.scrivtech.powerview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the aggregation on [ActionResult.Failed]. This is thin logic, but the
 * UI branches on it to decide between one "finish setup" prompt and a list of
 * per-shade failures, and [ActionRunner] itself cannot be unit tested without
 * an Android `Context`.
 *
 * JUnit 4 here, not the JUnit 5 that `:protocol` uses: `:data` is an Android
 * library and has no `useJUnitPlatform()`.
 */
class ActionResultTest {

    private val mac1 = "C6:83:B4:47:08:51"
    private val mac2 = "C6:83:B4:47:08:52"

    @Test
    fun `nothingAttempted is true when every shade was blocked before BLE`() {
        val result = ActionResult.Failed(
            mapOf(
                mac1 to CommandOutcome.NotAttempted.NoKeystream,
                mac2 to CommandOutcome.NotAttempted.NoKeystream,
            ),
        )

        assertTrue(result.nothingAttempted)
    }

    @Test
    fun `nothingAttempted mixes NotAttempted reasons`() {
        val result = ActionResult.Failed(
            mapOf(
                mac1 to CommandOutcome.NotAttempted.NoKeystream,
                mac2 to CommandOutcome.NotAttempted.BluetoothOff,
            ),
        )

        assertTrue(result.nothingAttempted)
    }

    /**
     * The distinction that matters physically: one shade was actually reached,
     * so the action is not simply "blocked on setup" and the user cannot be
     * told nothing moved.
     */
    @Test
    fun `nothingAttempted is false when any shade reached the transport`() {
        val result = ActionResult.Failed(
            mapOf(
                mac1 to CommandOutcome.NotAttempted.NoKeystream,
                mac2 to CommandOutcome.TransportFailed(CommandOutcome.TransportFailed.Stage.WRITE),
            ),
        )

        assertFalse(result.nothingAttempted)
    }

    @Test
    fun `failedMacAddresses exposes every failing shade for the widget path`() {
        val result = ActionResult.Failed(
            mapOf(
                mac1 to CommandOutcome.NotAttempted.UnknownShade,
                mac2 to CommandOutcome.TransportFailed(CommandOutcome.TransportFailed.Stage.CONNECT),
            ),
        )

        assertEquals(setOf(mac1, mac2), result.failedMacAddresses.toSet())
    }
}
