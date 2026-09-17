package com.scrivtech.powerview.data

import kotlinx.serialization.Serializable

/**
 * A saved, user-defined command — the model backing widgets, the Quick
 * Settings tile, shortcuts and notification actions (spec §3.1). Gen 3 has
 * no on-shade scenes, so a list of per-shade [Command]s *is* the scene.
 */
@Serializable
public data class ShadeAction(
    public val id: String,
    public val label: String,
    public val icon: ActionIcon,
    public val commands: List<Command>,
)

/** One shade's target position within a [ShadeAction]. Null fields are left untouched. */
@Serializable
public data class Command(
    public val macAddress: String,
    /** 0.0..100.0, or null to leave the primary rail alone. */
    public val primaryPercent: Double? = null,
    /** 0.0..100.0, or null to leave the secondary rail alone. */
    public val secondaryPercent: Double? = null,
    /** 0..100, or null to leave tilt alone. */
    public val tiltPercent: Int? = null,
)

/** Icon choices for a [ShadeAction] button (widget grid, Quick Settings tile, shortcuts). */
@Serializable
public enum class ActionIcon {
    UP,
    DOWN,
    STOP,
    HALF,
    CUSTOM,
}
