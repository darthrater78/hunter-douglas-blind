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
    /** Coarse battery bucket: 10/50/100 ~ low/medium/high, or null if never read. See spec §2.5. */
    public val batteryBucket: Int?,
    public val batteryReadAt: Instant?,
    /** Excludes this shade from the battery sweep and low-battery alerting (spec §2.5, "Hardwired shades"). */
    public val mainsPowered: Boolean,
)
