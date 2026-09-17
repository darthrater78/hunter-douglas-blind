package com.scrivtech.powerview.ble

import com.scrivtech.powerview.protocol.ShadeState

/** One decoded scan result: a shade's advertised state, plus the radio metadata around it. */
public data class RawAdvertisement(
    public val macAddress: String,
    public val rssi: Int,
    public val shadeState: ShadeState,
    /** Raw manufacturer-specific payload (company ID stripped), kept for the debug screen (build order step 2). */
    public val rawPayloadHex: String,
    public val timestampMillis: Long,
)
