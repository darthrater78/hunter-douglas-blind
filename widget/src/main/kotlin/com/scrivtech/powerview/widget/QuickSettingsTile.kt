package com.scrivtech.powerview.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.scrivtech.powerview.data.ActionStore
import com.scrivtech.powerview.data.SettingsStore
import com.scrivtech.powerview.data.ShadeAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A Quick Settings tile running one designated [ShadeAction] (build order
 * step 11, spec §3.5) — reachable from the lock screen, which is the point of
 * it.
 *
 * It goes through [CommandDispatch], exactly as a widget tap does, so the
 * tile is a second button on the same funnel rather than a second code path
 * to BLE.
 *
 * **Which action it runs is stored in [SettingsStore], not here.** A tile has
 * one button and no configuration surface of its own, so it is chosen in the
 * app's settings; the id is stored rather than the action, so renaming or
 * retargeting the action needs no reconfiguration.
 *
 * **The last run's outcome is held in memory and is deliberately not
 * persisted.** A tile is a singleton, so there is exactly one value, and it
 * is worth only as much as the process it was learned in: if the app has been
 * killed since, "last run failed" is stale news the user has no way to act on
 * from a tile. The app itself is the record, which is what the failure
 * subtitle points at.
 */
public class QuickSettingsTile : TileService() {

    // TileService is not a LifecycleService, so the scope is managed by hand.
    // Main.immediate because everything it does ends in a qsTile update, which
    // must happen on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()

        scope.launch {
            val actionId = SettingsStore(applicationContext).tileActionId.first()

            // Nothing designated, or it was deleted: the fix is in the app, so
            // go there rather than leaving a tap that does nothing at all.
            if (actionId == null || resolveAction(actionId) == null) {
                openApp()
                return@launch
            }

            // Same debounce reasoning as the widget: a second run costs
            // another connect/disconnect cycle on the shade's own battery.
            // CommandDispatch's unique work name is the backstop.
            if (lastRun == SlotRun.PENDING) return@launch

            lastRun = SlotRun.PENDING
            CommandDispatch.enqueue(applicationContext, actionId)
            render()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render() {
        // Null whenever the tile is not currently bound; there is simply
        // nothing to draw on, and onStartListening will come round again.
        val tile = qsTile ?: return

        val actionId = SettingsStore(applicationContext).tileActionId.first()
        val action = actionId?.let { resolveAction(it) }

        tile.label = action?.label ?: TILE_UNCONFIGURED_LABEL
        tile.state = if (action != null && lastRun == SlotRun.PENDING) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        val subtitle = tileSubtitle(hasAction = action != null, run = lastRun)
        // Tile.subtitle is API 29. Below that the label carries everything, so
        // there is nothing to fall back to and nothing lost.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.contentDescription = "${tile.label}. $subtitle"

        tile.updateTile()
    }

    private suspend fun resolveAction(actionId: String): ShadeAction? =
        ActionStore(applicationContext).actions.first().firstOrNull { it.id == actionId }

    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34 replaced the Intent overload with a PendingIntent one and
            // deprecated the original; calling the old one there throws.
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    public companion object {

        /**
         * Volatile rather than synchronised: it is written from a worker's
         * coroutine and read on the main thread, and a single reference needs
         * visibility rather than mutual exclusion.
         */
        @Volatile
        private var lastRun: SlotRun = SlotRun.IDLE

        /**
         * Called by [CommandWorker] when a run settles. Ignores actions the
         * tile is not showing, so an unrelated widget tap does not repaint it.
         *
         * [TileService.requestListeningState] is the only way to make the
         * system call back into a tile that is not currently bound; without
         * it the tile would show the outcome of the run before last.
         */
        internal suspend fun report(context: Context, actionId: String, run: SlotRun) {
            val designated = SettingsStore(context).tileActionId.first()
            if (designated != actionId) return

            lastRun = run

            ignoringDisplayFailures {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, QuickSettingsTile::class.java),
                )
            }
        }
    }
}
