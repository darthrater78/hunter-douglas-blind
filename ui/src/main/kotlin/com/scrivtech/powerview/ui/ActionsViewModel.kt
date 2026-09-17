package com.scrivtech.powerview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrivtech.powerview.data.ActionIcon
import com.scrivtech.powerview.data.ActionResult
import com.scrivtech.powerview.data.ActionRunner
import com.scrivtech.powerview.data.ActionStore
import com.scrivtech.powerview.data.ShadeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Which field of a [CommandDraft] an editor control is bound to. */
internal enum class DraftField { PRIMARY, SECONDARY, TILT }

/** Transient state for actions being run (spec §3.3's pending/success/failed). */
public data class ActionRunUiState(
    public val inFlight: Set<String> = emptySet(),
    public val results: Map<String, ActionResult> = emptyMap(),
)

/**
 * Backs the saved-actions list and its editor (build order step 7's
 * `ShadeAction` model, made reachable).
 *
 * The in-progress draft lives here rather than in composable state so that
 * rotating the device mid-edit does not discard it — a nested map of per-shade,
 * per-field toggles is not something `rememberSaveable` can carry, and losing
 * a half-built action is a much bigger annoyance than losing a text field.
 */
public class ActionsViewModel(
    private val actionStore: ActionStore,
    private val actionRunner: ActionRunner,
) : ViewModel() {

    public val actions: StateFlow<List<ShadeAction>> = actionStore.actions
        .map { list -> list.sortedBy { it.label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    private val _runs = MutableStateFlow(ActionRunUiState())
    public val runs: StateFlow<ActionRunUiState> = _runs.asStateFlow()

    private val _draft = MutableStateFlow<ActionDraft?>(null)
    internal val draft: StateFlow<ActionDraft?> = _draft.asStateFlow()

    /**
     * Opens an empty draft seeded with every known shade, all fields disabled.
     * Seeding the full list is what lets the editor show a row per shade while
     * [toShadeAction] drops the ones the user never touched.
     */
    internal fun startNewAction(shadeMacAddresses: List<String>) {
        _draft.value = ActionDraft(
            id = UUID.randomUUID().toString(),
            commands = shadeMacAddresses.map(::CommandDraft),
        )
    }

    /**
     * Opens [actionId] for editing, seeded with every known shade so shades can
     * be added to an existing action. Shades the action already commands keep
     * their stored values; a shade the action does not mention, or one that has
     * since been forgotten, appears with its fields off.
     */
    internal fun editAction(actionId: String, shadeMacAddresses: List<String>) {
        viewModelScope.launch {
            val existing = actionStore.actions.first().firstOrNull { it.id == actionId } ?: return@launch
            val stored = existing.toDraft()
            val storedByMac = stored.commands.associateBy { it.macAddress }

            // Union, existing shades first, so a command targeting a forgotten
            // shade is not silently dropped just by opening the editor.
            val macs = stored.commands.map { it.macAddress } +
                shadeMacAddresses.filterNot { it in storedByMac }

            _draft.value = stored.copy(
                commands = macs.map { mac -> storedByMac[mac] ?: CommandDraft(mac) },
            )
        }
    }

    internal fun cancelEdit() {
        _draft.value = null
    }

    internal fun updateDraftLabel(label: String) {
        _draft.update { it?.copy(label = label) }
    }

    internal fun updateDraftIcon(icon: ActionIcon) {
        _draft.update { it?.copy(icon = icon) }
    }

    internal fun setFieldEnabled(macAddress: String, field: DraftField, enabled: Boolean) {
        updateField(macAddress, field) { it.copy(enabled = enabled) }
    }

    internal fun setFieldPercent(macAddress: String, field: DraftField, percent: Double) {
        // Moving a slider is an expression of intent about that field, so it
        // enables it: a user who drags tilt to 30% and then finds tilt was
        // never included would reasonably call that a bug.
        updateField(macAddress, field) { it.copy(enabled = true, percent = percent) }
    }

    private fun updateField(
        macAddress: String,
        field: DraftField,
        transform: (FieldDraft) -> FieldDraft,
    ) {
        _draft.update { current ->
            current?.copy(
                commands = current.commands.map { command ->
                    if (command.macAddress != macAddress) {
                        command
                    } else {
                        when (field) {
                            DraftField.PRIMARY -> command.copy(primary = transform(command.primary))
                            DraftField.SECONDARY -> command.copy(secondary = transform(command.secondary))
                            DraftField.TILT -> command.copy(tilt = transform(command.tilt))
                        }
                    }
                },
            )
        }
    }

    /** Saves the draft if it validates; a caller should be gating on [ActionDraft.validationError] already. */
    internal fun saveDraft() {
        val draft = _draft.value ?: return
        if (draft.validationError() != null) return

        viewModelScope.launch {
            actionStore.upsert(draft.toShadeAction())
            _draft.value = null
        }
    }

    public fun deleteAction(actionId: String) {
        viewModelScope.launch {
            actionStore.remove(actionId)
            _runs.update { it.copy(results = it.results - actionId) }
        }
    }

    /**
     * Runs [action] now. Named `runAction` rather than `run` so a bound
     * reference cannot be confused with the stdlib's `run` extension. Ignores a
     * second press while it is in flight: an
     * action is a sequence of connect/write/disconnect cycles, and overlapping
     * runs would have the shades fighting each other.
     */
    public fun runAction(action: ShadeAction) {
        if (action.id in _runs.value.inFlight) return

        _runs.update {
            it.copy(inFlight = it.inFlight + action.id, results = it.results - action.id)
        }

        viewModelScope.launch {
            val result = actionRunner.run(action)
            _runs.update {
                it.copy(
                    inFlight = it.inFlight - action.id,
                    results = it.results + (action.id to result),
                )
            }
        }
    }

    public fun clearRunResult(actionId: String) {
        _runs.update { it.copy(results = it.results - actionId) }
    }

    public class Factory(
        private val actionStore: ActionStore,
        private val actionRunner: ActionRunner,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActionsViewModel::class.java))
            return ActionsViewModel(actionStore, actionRunner) as T
        }
    }
}
