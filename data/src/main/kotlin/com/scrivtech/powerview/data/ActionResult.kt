package com.scrivtech.powerview.data

/**
 * Why one [Command] did or did not reach a shade.
 *
 * The important line in here is [NotAttempted] versus [TransportFailed], and
 * it is a line about the physical world rather than about error handling.
 * These commands move something. A [NotAttempted] outcome means nothing was
 * transmitted and the shade certainly did not move; a [TransportFailed] one
 * means a frame may or may not have landed before the connection broke, and
 * the shade's real position is unknown until the next advertisement arrives.
 * A UI that collapses the two is telling the user something it does not know.
 *
 * [ActionRunner] previously returned a bare `Boolean` here and told callers
 * wanting detail to go and inspect [ShadeStore]/[KeystreamStore] themselves.
 * That was survivable for a widget, whose only honest report is a failure
 * icon anyway, but an in-app button needs to tell "this home has no keystream
 * yet" (the user has not finished onboarding, and no amount of retrying will
 * help) apart from "the shade is out of range" (try again, closer).
 */
public sealed interface CommandOutcome {

    /** The write completed. The shade acknowledged the frame. */
    public data object Sent : CommandOutcome

    /**
     * Nothing was transmitted, and the reason was knowable without touching
     * BLE at all. Every case here is a configuration or permission problem
     * that retrying cannot fix — so a UI should describe the fix rather than
     * offering a retry button.
     */
    public sealed interface NotAttempted : CommandOutcome {

        /** No persisted metadata for this MAC — it has never been seen or saved. */
        public data object UnknownShade : NotAttempted

        /**
         * Known shade, but no `homeId` recorded yet. `homeId` only ever arrives
         * from an advertisement, so this resolves itself once the shade is in
         * range and a scan runs — unlike its siblings, this one really does
         * fix itself.
         */
        public data object NoHomeId : NotAttempted

        /**
         * No keystream stored for this shade's home: onboarding (build order
         * step 5, `docs/PROTOCOL.md` §7) has not been done. Until that lands
         * this is the expected outcome of every command in the app, which is
         * exactly why it needs to be nameable.
         */
        public data object NoKeystream : NotAttempted

        /** `BLUETOOTH_CONNECT` not granted. Prompt for it. */
        public data object MissingConnectPermission : NotAttempted

        /** No Bluetooth adapter, or the adapter is switched off. */
        public data object BluetoothOff : NotAttempted

        /** The stored MAC is not a well-formed address — a corrupt or hand-edited store. */
        public data object MalformedAddress : NotAttempted
    }

    /**
     * BLE was attempted and failed. Most often the shade is out of range or
     * asleep (spec §3.4). [stage] says how far the exchange got, which is what
     * decides whether the shade could possibly have moved.
     */
    public data class TransportFailed(public val stage: Stage) : CommandOutcome {
        public enum class Stage {
            /** Never connected — the shade did not move. */
            CONNECT,

            /** Connected but service discovery failed — the shade did not move. */
            DISCOVER,

            /**
             * The write itself failed or was not acknowledged. This is the one
             * genuinely ambiguous outcome: the frame may have been delivered
             * and only the acknowledgement lost.
             */
            WRITE,

            /** The permission was revoked mid-exchange. */
            PERMISSION_REVOKED,
        }
    }
}

/**
 * Outcome of running a whole [ShadeAction]. Mirrors the widget state machine
 * in spec §3.3: `Pending` while [ActionRunner] is working, then
 * `Success`/`Failed` once every command has been attempted.
 */
public sealed interface ActionResult {

    public data object Pending : ActionResult

    public data object Success : ActionResult

    /**
     * At least one command did not land. [outcomes] holds every failure by MAC
     * so an in-app caller can explain each one; [failedMacAddresses] is the
     * flattened view the widget and [CommandWorker] paths want, since a
     * home-screen surface has nowhere to put six different explanations.
     */
    public data class Failed(public val outcomes: Map<String, CommandOutcome>) : ActionResult {
        public val failedMacAddresses: List<String> get() = outcomes.keys.toList()

        /**
         * True when nothing was transmitted for any shade in the action. The
         * useful case is a whole action blocked on onboarding: every command
         * comes back [CommandOutcome.NotAttempted.NoKeystream], and the right
         * response is one "finish setup" prompt rather than N failure rows.
         */
        public val nothingAttempted: Boolean
            get() = outcomes.values.all { it is CommandOutcome.NotAttempted }
    }
}
