package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.ActionIcon
import com.scrivtech.powerview.data.Command
import com.scrivtech.powerview.data.ShadeAction
import kotlin.math.roundToInt

/**
 * One editable field of one shade's command.
 *
 * [enabled] is the whole point of this type. A [Command] field is nullable, and
 * null means "leave this rail where it is" — a real instruction to the shade,
 * not an absence of one. The editor therefore cannot represent a field as just
 * a number: "tilt 0%" and "don't touch the tilt" are different commands, and a
 * slider alone cannot say which is meant.
 */
internal data class FieldDraft(
    val enabled: Boolean = false,
    val percent: Double = 50.0,
)

/** One shade's contribution to an action while it is being edited. */
internal data class CommandDraft(
    val macAddress: String,
    val primary: FieldDraft = FieldDraft(),
    val secondary: FieldDraft = FieldDraft(),
    val tilt: FieldDraft = FieldDraft(),
) {
    /** A shade with nothing enabled is not part of the action, however it got into the list. */
    val hasAnyField: Boolean get() = primary.enabled || secondary.enabled || tilt.enabled
}

/**
 * A [ShadeAction] mid-edit. Held in the view model rather than in composable
 * state so an in-progress edit survives rotation.
 */
internal data class ActionDraft(
    val id: String,
    val label: String = "",
    val icon: ActionIcon = ActionIcon.CUSTOM,
    val commands: List<CommandDraft> = emptyList(),
)

/**
 * Drops shades with no enabled field. They are carried through the editor so a
 * shade's sliders keep their positions while the user toggles fields off and
 * on, but a [Command] with every field null tells the shade nothing and would
 * still cost a full connect/disconnect cycle to deliver.
 */
internal fun ActionDraft.toShadeAction(): ShadeAction = ShadeAction(
    id = id,
    label = label.trim(),
    icon = icon,
    commands = commands.filter { it.hasAnyField }.map { draft ->
        Command(
            macAddress = draft.macAddress,
            primaryPercent = draft.primary.takeIf { it.enabled }?.percent,
            secondaryPercent = draft.secondary.takeIf { it.enabled }?.percent,
            tiltPercent = draft.tilt.takeIf { it.enabled }?.percent?.roundToInt(),
        )
    },
)

internal fun ShadeAction.toDraft(): ActionDraft = ActionDraft(
    id = id,
    label = label,
    icon = icon,
    commands = commands.map { command ->
        CommandDraft(
            macAddress = command.macAddress,
            primary = command.primaryPercent.toFieldDraft(),
            secondary = command.secondaryPercent.toFieldDraft(),
            tilt = command.tiltPercent?.toDouble().toFieldDraft(),
        )
    },
)

private fun Double?.toFieldDraft(): FieldDraft =
    if (this == null) FieldDraft() else FieldDraft(enabled = true, percent = this)

/**
 * Why this draft cannot be saved yet, or null if it can. Returned as text
 * because there is nothing the caller can do with a richer type — the editor
 * shows it and disables Save.
 */
internal fun ActionDraft.validationError(): String? = when {
    label.isBlank() -> "Give the action a name."
    commands.none { it.hasAnyField } ->
        "Choose at least one shade and set at least one of its positions."
    else -> null
}
