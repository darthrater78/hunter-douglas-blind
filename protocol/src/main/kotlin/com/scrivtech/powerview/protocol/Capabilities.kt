package com.scrivtech.powerview.protocol

/**
 * Which controls to show for a shade, derived from its capability number.
 * Ported from the openHAB binding's `ShadeCapabilitiesDatabase` (EPL-2.0 —
 * reimplemented from its behavior, not copied). See `docs/PROTOCOL.md` §1.5.
 */
public enum class ShadeCapability(
    public val id: Int,
    public val hasPrimaryRail: Boolean,
    public val hasSecondaryRail: Boolean,
    public val hasTilt: Boolean,
    /** 90 or 180, or null if [hasTilt] is false. */
    public val tiltRangeDegrees: Int?,
    /** Top-down shades report primary position inverted relative to a normal bottom-up shade. */
    public val primaryInverted: Boolean = false,
    /** Dual shades (e.g. Duolite) where the secondary rail overlaps the primary. */
    public val overlapped: Boolean = false,
) {
    BOTTOM_UP(0, hasPrimaryRail = true, hasSecondaryRail = false, hasTilt = false, tiltRangeDegrees = null),
    BOTTOM_UP_TILT_90(1, hasPrimaryRail = true, hasSecondaryRail = false, hasTilt = true, tiltRangeDegrees = 90),
    BOTTOM_UP_TILT_180(2, hasPrimaryRail = true, hasSecondaryRail = false, hasTilt = true, tiltRangeDegrees = 180),
    VERTICAL(3, hasPrimaryRail = true, hasSecondaryRail = false, hasTilt = false, tiltRangeDegrees = null),
    VERTICAL_TILT_180(4, hasPrimaryRail = true, hasSecondaryRail = false, hasTilt = true, tiltRangeDegrees = 180),
    TILT_ONLY(5, hasPrimaryRail = false, hasSecondaryRail = false, hasTilt = true, tiltRangeDegrees = 180),
    TOP_DOWN(6, hasPrimaryRail = true, hasSecondaryRail = false, hasTilt = false, tiltRangeDegrees = null, primaryInverted = true),
    TOP_DOWN_BOTTOM_UP(7, hasPrimaryRail = true, hasSecondaryRail = true, hasTilt = false, tiltRangeDegrees = null),
    DUAL_OVERLAPPED(8, hasPrimaryRail = true, hasSecondaryRail = true, hasTilt = false, tiltRangeDegrees = null, overlapped = true),
    DUAL_OVERLAPPED_TILT_90(9, hasPrimaryRail = true, hasSecondaryRail = true, hasTilt = true, tiltRangeDegrees = 90, overlapped = true),
    DUAL_OVERLAPPED_TILT_180(10, hasPrimaryRail = true, hasSecondaryRail = true, hasTilt = true, tiltRangeDegrees = 180, overlapped = true),
    ;

    public companion object {
        public fun forId(id: Int): ShadeCapability? = entries.firstOrNull { it.id == id }
    }
}

/** Result of [Capabilities.forTypeId]: the resolved capability plus whether the lookup actually matched a known type. */
public data class CapabilityLookup(
    public val capability: ShadeCapability,
    public val isKnownType: Boolean,
)

/**
 * Maps a shade's advertised `typeId` (see [ShadeState.typeId]) to its
 * [ShadeCapability]. Table below is abbreviated from the spec/openHAB
 * `ShadeCapabilitiesDatabase` — extend as new type IDs are confirmed on
 * real hardware.
 */
public object Capabilities {

    /** typeId -> capability id, as a direct table (some entries are documented "overrides", not a pattern-derived value). */
    private val TYPE_TO_CAPABILITY: Map<Int, Int> = buildMap {
        put(1, 0)   // Roller/Solar
        put(4, 0)   // Roman
        put(6, 0)   // Duette
        put(7, 6)   // Top Down
        put(8, 7)   // Duette TDBU
        put(18, 1)  // Pirouette
        put(23, 1)  // Silhouette
        put(31, 0)  // Vignette
        put(32, 0)  // Vignette
        put(33, 7)  // Duette Architella
        put(38, 9)  // Silhouette Duolite
        put(39, 5)  // Parkland
        put(40, 5)  // Everwood
        put(43, 1)  // Facette
        put(44, 1)  // Twist (override)
        put(49, 0)  // AC Roller
        put(51, 2)  // Venetian
        put(52, 0)  // Banded
        put(53, 0)  // Sonnette
        put(54, 4)  // Vertical Slats (override)
        put(55, 4)  // Vertical Slats (override)
        put(56, 4)  // Vertical Slats (override)
        put(62, 2)  // Venetian
        put(65, 8)  // Vignette Duolite
        put(66, 5)  // Shutter
        put(69, 3)  // Curtain
        put(70, 3)  // Curtain
        put(71, 3)  // Curtain
        put(79, 8)  // Duolite Lift
        put(84, 0)  // Vignette
        put(95, 8)  // Vignette Duolite Illuminated
    }

    private val FALLBACK = ShadeCapability.BOTTOM_UP

    /**
     * Resolves [typeId] to its capability. Unknown type IDs fall back to
     * [ShadeCapability.BOTTOM_UP] (primary rail only) with
     * [CapabilityLookup.isKnownType] = false — callers should log this so
     * unrecognized hardware gets reported rather than silently mis-rendered.
     */
    public fun forTypeId(typeId: Int): CapabilityLookup {
        val capabilityId = TYPE_TO_CAPABILITY[typeId]
        val capability = capabilityId?.let { ShadeCapability.forId(it) }
        return if (capability != null) {
            CapabilityLookup(capability, isKnownType = true)
        } else {
            CapabilityLookup(FALLBACK, isKnownType = false)
        }
    }
}
