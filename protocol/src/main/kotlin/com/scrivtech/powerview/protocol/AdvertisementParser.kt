package com.scrivtech.powerview.protocol

/**
 * Decodes the manufacturer-specific advertisement payload PowerView Gen 3
 * shades broadcast continuously and unencrypted. See `docs/PROTOCOL.md` §1.2.
 *
 * Two offset tables exist because Android's own BLE API strips the 2-byte
 * company ID before handing you the payload, while raw sniffs (and the
 * openHAB binding this was derived from) include it. Use [parseAndroidPayload]
 * with `ScanRecord.getManufacturerSpecificData(MANUFACTURER_ID)`; use
 * [parseFullPayload] if you have the company ID still attached (e.g. a raw
 * HCI snoop capture).
 */
public object AdvertisementParser {

    /** Hunter Douglas BLE manufacturer (company) ID. */
    public const val MANUFACTURER_ID: Int = 0x0819

    // Offsets within the payload as returned by Android's
    // ScanRecord.getManufacturerSpecificData(MANUFACTURER_ID) — i.e. with the
    // 2-byte company ID already stripped.
    private const val OFFSET_HOME_ID = 0
    private const val OFFSET_TYPE_ID = 2
    private const val OFFSET_PRIMARY = 3
    private const val OFFSET_SECONDARY = 5
    private const val OFFSET_TILT = 7
    private const val OFFSET_VELOCITY = 8

    /** Minimum payload length to decode homeId, typeId and primary position. */
    private const val MIN_LENGTH = OFFSET_PRIMARY + 2

    /**
     * Parses a payload that still has the 2-byte manufacturer ID at the
     * front (the "openHAB offset" table in the spec). Returns null if the
     * manufacturer ID doesn't match [MANUFACTURER_ID] or the payload is too
     * short to be a shade advertisement.
     */
    public fun parseFullPayload(payload: ByteArray): ShadeState? {
        if (payload.size < 2) return null
        val manufacturerId = readUInt16LE(payload, 0) ?: return null
        if (manufacturerId != MANUFACTURER_ID) return null
        return parseAndroidPayload(payload.copyOfRange(2, payload.size))
    }

    /**
     * Parses a payload as returned by
     * `ScanRecord.getManufacturerSpecificData(0x0819)` — company ID already
     * stripped. Returns null if the payload is too short to contain at least
     * homeId, typeId and a primary position.
     */
    public fun parseAndroidPayload(payload: ByteArray): ShadeState? {
        if (payload.size < MIN_LENGTH) return null

        val homeId = readUInt16LE(payload, OFFSET_HOME_ID) ?: return null
        val typeId = payload[OFFSET_TYPE_ID].toInt() and 0xFF
        val primaryRaw = readUInt16LE(payload, OFFSET_PRIMARY) ?: return null

        // Secondary, tilt and velocity are optional trailing fields — some
        // shade types (e.g. a bare bottom-up roller) may advertise a shorter
        // payload that never carries them.
        val secondaryRaw = readUInt16LE(payload, OFFSET_SECONDARY)
        val tiltRaw = readUInt8(payload, OFFSET_TILT)
        val velocityRaw = readUInt8(payload, OFFSET_VELOCITY)

        return ShadeState(
            homeId = homeId,
            typeId = typeId,
            primaryPercent = rawToRailPercent(primaryRaw),
            secondaryPercent = secondaryRaw?.let { rawToRailPercent(it) },
            tiltPercent = tiltRaw?.let { it.coerceIn(0, 100) },
            velocityRaw = velocityRaw,
        )
    }

    /** Rail (primary/secondary) raw value -> percent: `raw / 40.0`, clamped 0..100. */
    private fun rawToRailPercent(raw: Int): Double =
        (raw / 40.0).coerceIn(0.0, 100.0)

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 1 >= bytes.size) return null
        val lo = bytes[offset].toInt() and 0xFF
        val hi = bytes[offset + 1].toInt() and 0xFF
        return lo or (hi shl 8)
    }

    private fun readUInt8(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset >= bytes.size) return null
        return bytes[offset].toInt() and 0xFF
    }
}
