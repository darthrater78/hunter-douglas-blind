package com.scrivtech.powerview.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdvertisementParserTest {

    /** Builds a synthetic Android-offset payload: homeId, typeId, primary, secondary, tilt, velocity. */
    private fun buildPayload(
        homeId: Int,
        typeId: Int,
        primaryRaw: Int,
        secondaryRaw: Int? = null,
        tiltRaw: Int? = null,
        velocityRaw: Int? = null,
    ): ByteArray {
        val bytes = mutableListOf<Byte>()
        bytes += (homeId and 0xFF).toByte()
        bytes += ((homeId shr 8) and 0xFF).toByte()
        bytes += (typeId and 0xFF).toByte()
        bytes += (primaryRaw and 0xFF).toByte()
        bytes += ((primaryRaw shr 8) and 0xFF).toByte()
        if (secondaryRaw != null) {
            bytes += (secondaryRaw and 0xFF).toByte()
            bytes += ((secondaryRaw shr 8) and 0xFF).toByte()
        }
        if (tiltRaw != null) bytes += tiltRaw.toByte()
        if (velocityRaw != null) bytes += velocityRaw.toByte()
        return bytes.toByteArray()
    }

    @Test
    fun `decodes a fully populated payload`() {
        // primary raw 2000 -> 2000/40 = 50.0%; secondary raw 4000 -> 100.0%
        val payload = buildPayload(
            homeId = 0x1234,
            typeId = 6,
            primaryRaw = 2000,
            secondaryRaw = 4000,
            tiltRaw = 75,
            velocityRaw = 3,
        )

        val state = AdvertisementParser.parseAndroidPayload(payload)

        assertNotNull(state)
        state!!
        assertEquals(0x1234, state.homeId)
        assertEquals(6, state.typeId)
        assertEquals(50.0, state.primaryPercent)
        assertEquals(100.0, state.secondaryPercent)
        assertEquals(75, state.tiltPercent)
        assertEquals(3, state.velocityRaw)
    }

    @Test
    fun `decodes a short payload missing optional trailing fields`() {
        val payload = buildPayload(homeId = 1, typeId = 1, primaryRaw = 0)

        val state = AdvertisementParser.parseAndroidPayload(payload)

        assertNotNull(state)
        state!!
        assertNull(state.secondaryPercent)
        assertNull(state.tiltPercent)
        assertNull(state.velocityRaw)
    }

    @Test
    fun `returns null for a payload too short to contain even a primary position`() {
        val payload = byteArrayOf(0x01, 0x00, 0x06) // homeId + typeId only
        assertNull(AdvertisementParser.parseAndroidPayload(payload))
    }

    @Test
    fun `primary raw value out of the normal range is clamped to 100 percent`() {
        // raw / 40.0 for raw > 4000 would exceed 100% -- must clamp.
        val payload = buildPayload(homeId = 1, typeId = 1, primaryRaw = 5000)
        val state = AdvertisementParser.parseAndroidPayload(payload)
        assertEquals(100.0, state!!.primaryPercent)
    }

    @Test
    fun `parseFullPayload strips a matching manufacturer id and delegates`() {
        val androidPayload = buildPayload(homeId = 42, typeId = 6, primaryRaw = 0)
        val fullPayload = byteArrayOf(0x19, 0x08) + androidPayload // 0x0819 LE

        val state = AdvertisementParser.parseFullPayload(fullPayload)

        assertNotNull(state)
        assertEquals(42, state!!.homeId)
    }

    @Test
    fun `parseFullPayload rejects a non-Hunter-Douglas manufacturer id`() {
        val androidPayload = buildPayload(homeId = 42, typeId = 6, primaryRaw = 0)
        val fullPayload = byteArrayOf(0x00, 0x00) + androidPayload

        assertNull(AdvertisementParser.parseFullPayload(fullPayload))
    }
}
