package com.scrivtech.powerview.protocol

/**
 * Decoded contents of one PowerView Gen 3 manufacturer-specific BLE
 * advertisement. See [AdvertisementParser] for how this is produced and
 * `docs/PROTOCOL.md` §1.2 for the field layout this was decoded from.
 *
 * All position fields are already converted to human percentages; nothing
 * downstream of this class should need to touch a raw advertisement byte.
 */
public data class ShadeState(
    /** Identifies the "home" this shade is paired to. Shares one [homeId] -> keystream mapping. */
    val homeId: Int,
    /** Raw shade type byte from the advertisement. Feed to [Capabilities.forTypeId]. */
    val typeId: Int,
    /** Primary rail position, 0.0..100.0 (0 = fully open per PowerView convention). */
    val primaryPercent: Double,
    /** Secondary rail position, 0.0..100.0, or null if the advertisement didn't carry it. */
    val secondaryPercent: Double?,
    /** Tilt position, 0..100, or null if the advertisement didn't carry it. */
    val tiltPercent: Int?,
    /** Raw velocity byte. Semantics unconfirmed — see `docs/PROTOCOL.md` §5. */
    val velocityRaw: Int?,
)
