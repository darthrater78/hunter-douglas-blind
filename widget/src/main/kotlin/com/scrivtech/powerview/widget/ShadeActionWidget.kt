package com.scrivtech.powerview.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.scrivtech.powerview.data.ActionStore
import com.scrivtech.powerview.data.ShadeAction

/**
 * The home-screen widget: one to six buttons, each running a saved
 * [ShadeAction] (build order step 9, spec §3.2-§3.4).
 *
 * A tap never does BLE work itself. Glance callbacks run on a short leash and
 * a shade round trip takes seconds, so the callback marks the button pending
 * and hands off to [CommandWorker] through [CommandDispatch] — the same
 * `ActionRunner` funnel the in-app controls and the battery sweep use. The
 * result comes back to the widget through [WidgetStatus], which the worker
 * calls when it finishes.
 *
 * The state is per widget *instance* rather than global: two widgets can
 * point at the same action, and one being mid-run is not a fact about the
 * other's button. What that state looks like, and every decision about
 * laying it out, lives in `WidgetPresentation.kt` so it can be tested
 * without an Android SDK.
 *
 * `GlanceTheme` follows the launcher (dynamic colour on API 31+) rather than
 * the app's own theme setting. A widget sits on someone else's wallpaper; the
 * black OLED scheme would be wrong there as often as it was right.
 */
public class ShadeActionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Resolves to the same process-wide DataStore the rest of the app
        // uses, however many ActionStore wrappers exist — the same reason
        // BatterySweepWorker builds its own collaborators.
        val actionStore = ActionStore(context)

        provideContent {
            // Collected rather than read once: renaming an action in the app
            // should reach a widget pointing at it without waiting for
            // something else to trigger an update.
            val actions by actionStore.actions.collectAsState(initial = emptyList())
            val contents = decodeWidgetContents(currentState(CONTENTS_KEY))

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (contents.slots.isEmpty()) {
                        Caption(UNCONFIGURED_TEXT)
                    } else {
                        ActionGrid(contents, actions)
                    }
                }
            }
        }
    }

    public companion object {
        /** Persisted per widget instance; see `WidgetPresentation.kt` for the format. */
        internal val CONTENTS_KEY = stringPreferencesKey("widget_contents")

        /** Which action a tap refers to, carried through the Glance callback. */
        internal val ACTION_ID_PARAM = ActionParameters.Key<String>("action_id")
    }
}

/** The system entry point. Registered in this module's manifest. */
public class ShadeActionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShadeActionWidget()
}

@Composable
private fun ActionGrid(contents: WidgetContents, actions: List<ShadeAction>) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = gridRows(contents.slots)
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = GlanceModifier.size(4.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { cell, slot ->
                    if (cell > 0) Spacer(modifier = GlanceModifier.size(4.dp))
                    ActionButton(
                        slot = slot,
                        action = actions.firstOrNull { it.id == slot.actionId },
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    slot: WidgetSlot,
    action: ShadeAction?,
    modifier: GlanceModifier,
) {
    val enabled = isSlotEnabled(slot, action)

    // clickable() is attached only when the tap would do something. A button
    // that visibly reacts and then does nothing — because it is already
    // running, or its action was deleted — reads as a broken widget.
    val base = modifier
        .background(
            if (enabled) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.surfaceVariant,
        )
        .cornerRadius(12.dp)
        .padding(6.dp)

    val clickableModifier = if (enabled) {
        base.clickable(
            actionRunCallback<RunActionCallback>(
                actionParametersOf(ShadeActionWidget.ACTION_ID_PARAM to slot.actionId),
            ),
        )
    } else {
        base
    }

    Box(modifier = clickableModifier, contentAlignment = Alignment.Center) {
        Column(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = slotTitle(action),
                maxLines = 2,
                style = TextStyle(
                    color = if (enabled) {
                        GlanceTheme.colors.onSecondaryContainer
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
            )

            slotStatus(slot.run)?.let { status ->
                Text(
                    text = status,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

/**
 * Runs on a widget tap. Must return quickly — Glance gives a callback a short
 * window, and a BLE exchange takes seconds — so this only records that a run
 * started and enqueues the work.
 */
public class RunActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val actionId = parameters[ShadeActionWidget.ACTION_ID_PARAM] ?: return

        // The composition already hides the tap target for a pending slot;
        // this is the race behind it, where a second tap was dispatched
        // before the first re-render landed. CommandDispatch keeps a third
        // guard at the WorkManager level.
        if (WidgetStatus.isPending(context, glanceId, actionId)) return

        WidgetStatus.mark(context, actionId, SlotRun.PENDING)
        CommandDispatch.enqueue(context, actionId)
    }
}
