package com.scrivtech.powerview.ui

import com.scrivtech.powerview.data.ActionIcon
import com.scrivtech.powerview.data.Command
import com.scrivtech.powerview.data.ShadeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversion between what the editor holds and what gets persisted.
 *
 * The distinction under test throughout is enabled-versus-null. A null field in
 * a [Command] means "leave this rail alone" — an instruction, not a missing
 * value — so a round trip that turns "don't touch the tilt" into "tilt 0%"
 * would silently change what the action does to the hardware.
 */
class ActionDraftTest {

    private val mac1 = "C6:83:B4:47:08:51"
    private val mac2 = "C6:83:B4:47:08:52"

    @Test
    fun `a disabled field becomes null, not zero`() {
        val action = ActionDraft(
            id = "a",
            label = "Morning",
            commands = listOf(
                CommandDraft(
                    macAddress = mac1,
                    primary = FieldDraft(enabled = true, percent = 40.0),
                    tilt = FieldDraft(enabled = false, percent = 0.0),
                ),
            ),
        ).toShadeAction()

        val command = action.commands.single()
        assertEquals(40.0, command.primaryPercent!!, 0.001)
        assertNull(command.tiltPercent)
        assertNull(command.secondaryPercent)
    }

    @Test
    fun `shades with nothing enabled are dropped from the saved action`() {
        val action = ActionDraft(
            id = "a",
            label = "Morning",
            commands = listOf(
                CommandDraft(mac1, primary = FieldDraft(enabled = true, percent = 10.0)),
                CommandDraft(mac2),
            ),
        ).toShadeAction()

        assertEquals(listOf(mac1), action.commands.map { it.macAddress })
    }

    @Test
    fun `tilt is rounded to a whole percent`() {
        val action = ActionDraft(
            id = "a",
            label = "Morning",
            commands = listOf(
                CommandDraft(mac1, tilt = FieldDraft(enabled = true, percent = 49.6)),
            ),
        ).toShadeAction()

        assertEquals(50, action.commands.single().tiltPercent)
    }

    @Test
    fun `the label is trimmed`() {
        val action = ActionDraft(id = "a", label = "  Morning  ", commands = listOf(
            CommandDraft(mac1, primary = FieldDraft(enabled = true, percent = 10.0)),
        )).toShadeAction()

        assertEquals("Morning", action.label)
    }

    /** The round trip has to preserve which fields were instructions and which were not. */
    @Test
    fun `an action survives a round trip through the draft`() {
        val original = ShadeAction(
            id = "a",
            label = "Evening",
            icon = ActionIcon.DOWN,
            commands = listOf(
                Command(macAddress = mac1, primaryPercent = 100.0, tiltPercent = 25),
                Command(macAddress = mac2, secondaryPercent = 12.5),
            ),
        )

        assertEquals(original, original.toDraft().toShadeAction())
    }

    @Test
    fun `a draft from an existing action marks only its non-null fields enabled`() {
        val draft = ShadeAction(
            id = "a",
            label = "Evening",
            icon = ActionIcon.DOWN,
            commands = listOf(Command(macAddress = mac1, primaryPercent = 100.0)),
        ).toDraft()

        val command = draft.commands.single()
        assertTrue(command.primary.enabled)
        assertTrue(!command.secondary.enabled)
        assertTrue(!command.tilt.enabled)
    }

    // ---- validation ----

    @Test
    fun `a nameless action cannot be saved`() {
        val draft = ActionDraft(
            id = "a",
            label = "   ",
            commands = listOf(CommandDraft(mac1, primary = FieldDraft(enabled = true))),
        )

        assertNotNull(draft.validationError())
    }

    @Test
    fun `an action that would command nothing cannot be saved`() {
        val draft = ActionDraft(id = "a", label = "Morning", commands = listOf(CommandDraft(mac1)))

        assertNotNull(draft.validationError())
    }

    @Test
    fun `a complete draft validates`() {
        val draft = ActionDraft(
            id = "a",
            label = "Morning",
            commands = listOf(CommandDraft(mac1, primary = FieldDraft(enabled = true, percent = 0.0))),
        )

        assertNull(draft.validationError())
    }
}
