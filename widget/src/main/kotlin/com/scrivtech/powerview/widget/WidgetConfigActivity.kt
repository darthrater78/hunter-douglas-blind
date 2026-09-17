package com.scrivtech.powerview.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.scrivtech.powerview.data.ActionStore
import com.scrivtech.powerview.data.ShadeAction
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * The `android:configure` activity: picks which saved [ShadeAction]s a given
 * widget instance runs.
 *
 * Exported, because the launcher is what starts it — so the `appWidgetId` it
 * is handed is untrusted input. It is checked against the ids the system
 * actually reports for this provider before anything is written, so another
 * app cannot use this activity to rewrite a widget that is not ours. The
 * activity holds no permission and reveals nothing beyond the user's own
 * action labels, so that check is the whole of its attack surface.
 */
public class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // The widget-configuration contract: a cancelled configuration must
        // leave RESULT_CANCELED set, which is what tells the launcher to drop
        // the half-added widget instead of leaving a dead one on the screen.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val actionStore = ActionStore(this)

        setContent {
            val actions by actionStore.actions.collectAsState(initial = emptyList())

            MaterialTheme {
                Surface {
                    WidgetConfigScreen(
                        actions = actions.sortedBy { it.label },
                        onConfirm = { selected -> save(appWidgetId, selected) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun save(appWidgetId: Int, selectedIds: List<String>) {
        lifecycleScope.launch {
            val glanceId = resolveOwnWidget(appWidgetId)

            // Fail closed. An id this app's provider does not own gets no
            // write and no RESULT_OK -- the activity just goes away.
            if (glanceId == null) {
                finish()
                return@launch
            }

            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                val previous = decodeWidgetContents(prefs[ShadeActionWidget.CONTENTS_KEY])
                prefs[ShadeActionWidget.CONTENTS_KEY] =
                    encodeWidgetContents(configureSlots(previous, selectedIds))
            }

            // The state above is what the widget renders from, so a failed
            // redraw costs a stale frame, not a lost configuration.
            ignoringDisplayFailures {
                ShadeActionWidget().update(this@WidgetConfigActivity, glanceId)
            }

            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    /**
     * The untrusted-input check described in the class documentation: resolves
     * [appWidgetId] only if the system reports it as belonging to this app's
     * own widget provider, and null otherwise.
     */
    private suspend fun resolveOwnWidget(appWidgetId: Int) = try {
        val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
        manager.getGlanceIds(ShadeActionWidget::class.java)
            .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (expected: Exception) {
        null
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun WidgetConfigScreen(
    actions: List<ShadeAction>,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    // Order of selection is the order of the buttons, so this is a list
    // rather than a set: the user picking "Close all" first should get it in
    // the top-left cell.
    val selected = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item(key = "header") {
                Text(
                    if (actions.isEmpty()) {
                        "No saved actions yet. Create one in the app first — a widget " +
                            "runs an action, so there has to be one to point at."
                    } else {
                        "Choose up to $MAX_WIDGET_ACTIONS actions for this widget."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            items(actions, key = { it.id }) { action ->
                val isSelected = action.id in selected
                // Past the cap, unpicked rows stop responding rather than
                // silently dropping the extras at save time.
                val selectable = isSelected || selected.size < MAX_WIDGET_ACTIONS

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isSelected,
                                enabled = selectable,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    if (checked) selected.add(action.id) else selected.remove(action.id)
                                },
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null, enabled = selectable)
                        Text(
                            action.label,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Add widget")
            }
        }
    }
}
