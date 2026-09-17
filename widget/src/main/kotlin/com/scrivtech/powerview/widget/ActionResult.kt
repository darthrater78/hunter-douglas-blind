package com.scrivtech.powerview.widget

/**
 * Outcome of running a [com.scrivtech.powerview.data.ShadeAction]. Mirrors the
 * widget state machine in spec §3.3: `Pending` while `ActionRunner` is
 * working, then `Success`/`Failed` once every command has been attempted.
 */
public sealed interface ActionResult {
    public data object Pending : ActionResult
    public data object Success : ActionResult
    public data class Failed(public val failedMacAddresses: List<String>) : ActionResult
}
