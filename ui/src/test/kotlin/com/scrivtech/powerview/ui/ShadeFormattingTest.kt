package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.Shade
import com.scrivtech.powerview.protocol.CapabilityLookup
import com.scrivtech.powerview.protocol.ShadeCapability
import com.scrivtech.powerview.protocol.ShadeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Covers the pure logic behind the shade list: which rooms appear in which
 * order, and which position fields a shade's capability allows to be shown.
 *
 * The capability gating is the part worth pinning. Every advertisement carries
 * bytes for secondary and tilt whether or not the shade has them, so a screen
 * that renders them unconditionally shows a confident number for hardware that
 * does not exist — the debug screen labels those fields instead, but the
 * friendly list has to omit them.
 */
class ShadeFormattingTest {

    private fun shade(
        mac: String = "C6:83:B4:47:08:51",
        label: String = "Shade",
        room: String? = null,
        state: ShadeState? = null,
        capability: ShadeCapability? = null,
        batteryPercent: Int? = null,
        mainsPowered: Boolean = false,
        lastSeenAt: Instant? = null,
    ) = Shade(
        macAddress = mac,
        label = label,
        room = room,
        homeId = 63548,
        state = state,
        capabilities = capability?.let { CapabilityLookup(it, isKnownType = true) },
        lastSeenAt = lastSeenAt,
        batteryPercent = batteryPercent,
        batteryReadAt = null,
        mainsPowered = mainsPowered,
    )

    private fun state(
        primary: Double = 42.0,
        secondary: Double? = 17.0,
        tilt: Int? = 50,
    ) = ShadeState(
        homeId = 63548,
        typeId = 8,
        primaryPercent = primary,
        secondaryPercent = secondary,
        tiltPercent = tilt,
        velocityRaw = 0xC0,
    )

    // ---- groupIntoRooms ----

    @Test
    fun `rooms sort alphabetically regardless of case`() {
        val grouped = groupIntoRooms(
            listOf(
                shade(mac = "A", label = "a", room = "kitchen"),
                shade(mac = "B", label = "b", room = "Bedroom"),
                shade(mac = "C", label = "c", room = "Lounge"),
            ),
        )

        assertEquals(listOf("Bedroom", "kitchen", "Lounge"), grouped.map { it.first })
    }

    @Test
    fun `unfiled shades come last under a placeholder, not sorted among real rooms`() {
        val grouped = groupIntoRooms(
            listOf(
                shade(mac = "A", label = "a", room = null),
                shade(mac = "B", label = "b", room = "Zebra room"),
            ),
        )

        assertEquals(listOf("Zebra room", NO_ROOM_LABEL), grouped.map { it.first })
    }

    /** A room typed as spaces is not a room; it should not create an empty section. */
    @Test
    fun `blank room strings are treated as unfiled`() {
        val grouped = groupIntoRooms(listOf(shade(mac = "A", label = "a", room = "   ")))

        assertEquals(listOf(NO_ROOM_LABEL), grouped.map { it.first })
    }

    @Test
    fun `shades within a room sort by label`() {
        val grouped = groupIntoRooms(
            listOf(
                shade(mac = "A", label = "Window", room = "Lounge"),
                shade(mac = "B", label = "Door", room = "Lounge"),
            ),
        )

        assertEquals(listOf("Door", "Window"), grouped.single().second.map { it.label })
    }

    @Test
    fun `no unfiled section when every shade has a room`() {
        val grouped = groupIntoRooms(listOf(shade(room = "Lounge")))

        assertEquals(listOf("Lounge"), grouped.map { it.first })
    }

    // ---- positionSummary ----

    @Test
    fun `a bottom-up shade shows only its primary rail`() {
        val summary = positionSummary(
            shade(state = state(), capability = ShadeCapability.BOTTOM_UP),
        )

        assertEquals("Primary 42.0%", summary)
    }

    @Test
    fun `a top-down bottom-up shade shows both rails and no tilt`() {
        val summary = positionSummary(
            shade(state = state(), capability = ShadeCapability.TOP_DOWN_BOTTOM_UP),
        )

        assertTrue(summary, summary.contains("Primary 42.0%"))
        assertTrue(summary, summary.contains("Secondary 17.0%"))
        assertTrue(summary, !summary.contains("Tilt"))
    }

    @Test
    fun `a tilt-only shade shows tilt and no rails`() {
        val summary = positionSummary(
            shade(state = state(), capability = ShadeCapability.TILT_ONLY),
        )

        assertEquals("Tilt 50%", summary)
    }

    /**
     * Before any advertisement is decoded there is no capability to gate on.
     * Showing the primary rail is the useful default; inventing secondary and
     * tilt would be guessing at hardware that may not exist.
     */
    @Test
    fun `an undecoded capability falls back to the primary rail only`() {
        val summary = positionSummary(shade(state = state(), capability = null))

        assertEquals("Primary 42.0%", summary)
    }

    @Test
    fun `a shade not yet heard from has no position`() {
        assertEquals("Position unknown", positionSummary(shade(state = null)))
    }

    // ---- lastSeenText ----

    @Test
    fun `a shade never heard from this process is not reported as offline`() {
        assertEquals("Not seen yet", lastSeenText(shade(lastSeenAt = null)))
    }

    @Test
    fun `recent sightings read as in range`() {
        val now = Instant.parse("2026-09-17T12:00:00Z")
        val seen = shade(lastSeenAt = now.minusSeconds(3))

        assertEquals("In range", lastSeenText(seen, now))
    }

    @Test
    fun `older sightings degrade through seconds, minutes, hours and days`() {
        val now = Instant.parse("2026-09-17T12:00:00Z")

        assertEquals("Seen 30s ago", lastSeenText(shade(lastSeenAt = now.minusSeconds(30)), now))
        assertEquals("Seen 5m ago", lastSeenText(shade(lastSeenAt = now.minusSeconds(300)), now))
        assertEquals("Seen 2h ago", lastSeenText(shade(lastSeenAt = now.minusSeconds(7_200)), now))
        assertEquals("Seen 3d ago", lastSeenText(shade(lastSeenAt = now.minusSeconds(259_200)), now))
    }

    /** A clock correction must not render as a negative age. */
    @Test
    fun `a sighting in the future reads as in range`() {
        val now = Instant.parse("2026-09-17T12:00:00Z")
        val seen = shade(lastSeenAt = now.plusSeconds(60))

        assertEquals("In range", lastSeenText(seen, now))
    }

    // ---- battery ----

    @Test
    fun `a mains-powered shade reports mains rather than a battery percentage`() {
        assertEquals("Mains", batterySummary(shade(mainsPowered = true, batteryPercent = 65)))
    }

    @Test
    fun `an unread battery says so instead of showing zero`() {
        assertEquals("Battery not read", batterySummary(shade(batteryPercent = null)))
    }

    @Test
    fun `a read battery shows the percentage`() {
        assertEquals("65%", batterySummary(shade(batteryPercent = 65)))
    }
}
