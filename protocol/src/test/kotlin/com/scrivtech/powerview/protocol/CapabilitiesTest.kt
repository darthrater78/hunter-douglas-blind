package com.scrivtech.powerview.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CapabilitiesTest {

    @ParameterizedTest(name = "typeId {0} -> capability {1}")
    @CsvSource(
        "1, 0",   // Roller/Solar
        "6, 0",   // Duette
        "7, 6",   // Top Down
        "8, 7",   // Duette TDBU
        "18, 1",  // Pirouette
        "23, 1",  // Silhouette
        "31, 0",  // Vignette
        "38, 9",  // Silhouette Duolite
        "39, 5",  // Parkland
        "44, 1",  // Twist (override)
        "51, 2",  // Venetian
        "54, 4",  // Vertical Slats (override)
        "65, 8",  // Vignette Duolite
        "69, 3",  // Curtain
        "95, 8",  // Vignette Duolite Illuminated
    )
    fun `known type ids resolve to the documented capability`(typeId: Int, expectedCapabilityId: Int) {
        val lookup = Capabilities.forTypeId(typeId)
        assertTrue(lookup.isKnownType)
        assertEquals(expectedCapabilityId, lookup.capability.id)
    }

    @org.junit.jupiter.api.Test
    fun `unknown type id falls back to primary-only and reports itself as unknown`() {
        val lookup = Capabilities.forTypeId(9999)
        assertFalse(lookup.isKnownType)
        assertEquals(ShadeCapability.BOTTOM_UP, lookup.capability)
    }

    @org.junit.jupiter.api.Test
    fun `tilt-only capability has no primary rail`() {
        val lookup = Capabilities.forTypeId(39) // Parkland -> 5, tilt only
        assertFalse(lookup.capability.hasPrimaryRail)
        assertTrue(lookup.capability.hasTilt)
    }
}
