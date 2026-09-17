package com.scrivtech.powerview.data

import com.scrivtech.powerview.protocol.CapabilityLookup
import com.scrivtech.powerview.protocol.ShadeState
import java.time.Instant

/**
 * One shade as the app knows it: persisted user metadata ([ShadeStore])
 * merged with whatever live state has been observed ([ShadeRepository]).
 * `state` and `capabilities` are null until at least one advertisement has
 * been seen for this MAC in this process — see [ShadeRepository] for how a
 * cold-started widget (§3.4) should treat that.
 */
public data class Shade(
    public val macAddress: String,
    public val label: String,
    public val room: String?,
    public val homeId: Int?,
    public val state: ShadeState?,
    public val capabilities: CapabilityLookup?,
    public val lastSeenAt: Instant?,
    /** Battery percentage 0..100, or null if never read. See spec §2.5. */
    public val batteryPercent: Int?,
    public val batteryReadAt: Instant?,
    /** Excludes this shade from the battery sweep and low-battery alerting (spec §2.5, "Hardwired shades"). */
    public val mainsPowered: Boolean,
    /** RSSI of the most recent advertisement, or null if none has been seen this process. */
    public val lastRssi: Int? = null,
    /**
     * Raw manufacturer payload of the most recent advertisement, company ID
     * stripped, as space-separated hex. Carried per-shade purely so the
     * build-order-step-2 debug screen can show it: confirming the decode
     * against real hardware means comparing decoded fields to the bytes they
     * came from, and without these you can only see the parser's own opinion.
     */
    public val lastRawPayloadHex: String? = null,
)
