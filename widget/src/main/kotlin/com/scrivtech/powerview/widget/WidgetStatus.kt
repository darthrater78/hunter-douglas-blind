package com.scrivtech.powerview.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlin.coroutines.cancellation.CancellationException

/**
 * Writes a run's progress back onto every widget that shows the action.
 *
 * Every widget, not just the one that was tapped: two widgets can point at
 * the same action, and a shade moving because of a tap on one of them is a
 * fact about the action, not about which button was pressed. The alternative
 * — threading the tapped `GlanceId` through `WorkManager` input data — would
 * also leave the other widget stuck showing a stale state forever.
 *
 * No failure here is allowed to escape. It is called from a Glance callback
 * and from a `WorkManager` worker, neither of which has a user to report to,
 * and failing to update a *status indicator* must not take down a command
 * that may already have moved a shade. One widget that cannot be updated also
 * must not stop the others from being updated.
 */
internal object WidgetStatus {

    /**
     * Sets [actionId]'s state on every widget carrying it, then redraws those
     * widgets. Widgets that do not show the action are left untouched, which
     * also means they are not needlessly redrawn.
     */
    suspend fun mark(context: Context, actionId: String, run: SlotRun) {
        val widget = ShadeActionWidget()

        for (glanceId in glanceIds(context)) {
            ignoringDisplayFailures {
                if (!shows(read(context, glanceId), actionId)) return@ignoringDisplayFailures

                updateAppWidgetState(context, glanceId) { prefs ->
                    // Re-decoded inside the edit rather than reusing the copy
                    // read above: between that read and here, another tap or
                    // a finishing worker may have written, and rewriting the
                    // stale copy would undo it.
                    val current = decodeWidgetContents(prefs[ShadeActionWidget.CONTENTS_KEY])
                    prefs[ShadeActionWidget.CONTENTS_KEY] =
                        encodeWidgetContents(withRunState(current, actionId, run))
                }

                widget.update(context, glanceId)
            }
        }
    }

    /** Whether this particular widget already has [actionId] in flight. */
    suspend fun isPending(context: Context, glanceId: GlanceId, actionId: String): Boolean =
        isPending(read(context, glanceId), actionId)

    /** An unreadable widget reads as empty, which makes it a no-op rather than a crash. */
    private suspend fun read(context: Context, glanceId: GlanceId): WidgetContents {
        var contents = WidgetContents()
        ignoringDisplayFailures {
            contents = decodeWidgetContents(
                getAppWidgetState(
                    context = context,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId,
                )[ShadeActionWidget.CONTENTS_KEY],
            )
        }
        return contents
    }

    private suspend fun glanceIds(context: Context): List<GlanceId> {
        var ids = emptyList<GlanceId>()
        ignoringDisplayFailures {
            ids = GlanceAppWidgetManager(context).getGlanceIds(ShadeActionWidget::class.java)
        }
        return ids
    }
}

/**
 * Runs [block], discarding any failure but **not** cancellation.
 *
 * `runCatching` would be shorter and wrong here: it swallows
 * [CancellationException] along with everything else, so a coroutine that had
 * already been cancelled — a `WorkManager` worker that was stopped, an
 * activity that finished — would carry on doing work instead of unwinding.
 * Cancellation is control flow, not a failure.
 *
 * Used only for updating what a widget *displays*. Nothing that decides
 * whether a shade moves goes through here.
 */
internal suspend inline fun ignoringDisplayFailures(block: () -> Unit) {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (expected: Exception) {
        // Deliberately dropped: see the doc comment.
    }
}
