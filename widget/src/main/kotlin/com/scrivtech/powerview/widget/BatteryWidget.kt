package com.scrivtech.powerview.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.scrivtech.powerview.data.SettingsStore
import com.scrivtech.powerview.data.ShadeStore
import com.scrivtech.powerview.data.SweepInterval

/**
 * The declared sizes [SizeMode.Responsive] composes for. Approximate cell
 * math (`70dp * cells - 30dp`) matches `battery_widget_info.xml`'s
 * `minWidth`/`minHeight` (3x2 cells = 180x110dp) exactly at the small end,
 * then spans up toward a size that can show several rows comfortably.
 */
private val BATTERY_WIDGET_SIZES = setOf(
    DpSize(110.dp, 110.dp),
    DpSize(180.dp, 110.dp),
    DpSize(180.dp, 180.dp),
    DpSize(250.dp, 180.dp),
    DpSize(250.dp, 250.dp),
)

/**
 * Roughly one row's worth of vertical space: [BatteryRowView]'s 3dp vertical
 * padding either side of the taller of its 13sp label and its padded badge.
 */
private const val ROW_HEIGHT_DP: Float = 24f

/** The header line, the spacer under it, and the column's own padding — space no row gets to use. */
private const val HEADER_OVERHEAD_DP: Float = 54f

/** The "N more — open the app" line: 11sp text plus its 4dp top padding. */
private const val OVERFLOW_LINE_DP: Float = 20f

/**
 * How many of [totalRows] [BatteryRow]s to show in [height], replacing the
 * old size-blind [MAX_BATTERY_ROWS] constant now that this widget actually
 * recomposes when resized.
 *
 * When not every row fits, room is kept for the overflow line first: that
 * line is what says the list is incomplete, so it is the last thing that
 * should be clipped off the bottom. Bounded above by twice
 * [MAX_BATTERY_ROWS]: a very tall widget is still a glance, not a scrolling
 * list, so more room buys more rows only up to a point.
 */
internal fun maxRowsForHeight(height: Dp, totalRows: Int): Int {
    val available = (height.value - HEADER_OVERHEAD_DP).coerceAtLeast(0f)
    val fitsAll = (available / ROW_HEIGHT_DP).toInt()
    val rows = if (totalRows <= fitsAll) {
        fitsAll
    } else {
        ((available - OVERFLOW_LINE_DP).coerceAtLeast(0f) / ROW_HEIGHT_DP).toInt()
    }
    return rows.coerceIn(1, MAX_BATTERY_ROWS * 2)
}

/**
 * A home-screen widget answering one question: does anything need new
 * batteries?
 *
 * This is the surface build order step 10 was for. The weekly sweep and the
 * low-battery notification already exist; this is the at-a-glance view
 * between them — a notification only fires when something crosses the
 * threshold, and the app has to be opened.
 *
 * **It never connects to a shade.** Reading a battery means a full
 * connect/disconnect cycle, which spends the very thing being measured, so
 * this widget renders only what `ShadeStore` already holds and leaves the
 * measuring to `BatterySweepWorker` — which updates it when a sweep lands.
 * That is also why `updatePeriodMillis` is 0 and why a tap opens the app
 * rather than refreshing: the per-shade "Read battery" button in there is
 * the deliberate, one-shade-at-a-time way to spend that power.
 *
 * Redraws are pushed by `PowerViewApplication`, which watches `ShadeStore`
 * and calls [refreshBatteryWidgets] when it changes. That is indirect on
 * purpose: `BatterySweepWorker` lives in `:data`, which cannot see `:widget`,
 * and the sweep runs in the app process so the collector is there to hear
 * it.
 *
 * Two things it refuses to imply, both in `BatteryWidgetPresentation.kt`: a
 * shade that has never been read is not at 0%, and a reading two sweeps old
 * is shown with its age rather than as a current number.
 *
 * [sizeMode] is [SizeMode.Responsive] rather than the default
 * [SizeMode.Single] for the same reason as [ShadeActionWidget]: without it,
 * resizing in the launcher moves the outline but never reaches the
 * composition, which is what "too large and can't be resized" actually was.
 * [BATTERY_WIDGET_SIZES] spans the declared minimum up to a size that can
 * comfortably show several rows.
 */
public class BatteryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(BATTERY_WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val shadeStore = ShadeStore(context)
        val settingsStore = SettingsStore(context)

        // Built here rather than in the composition: the launcher activity
        // lives in :app, which :widget cannot reference, so it is resolved
        // by package instead of by class name. A hardcoded
        // "com.scrivtech.powerview.app.MainActivity" would compile fine and
        // break silently the day that class is renamed.
        val openApp = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        provideContent {
            val shades by shadeStore.shades.collectAsState(initial = emptyMap())
            // The staleness threshold follows the user's sweep interval, so
            // a monthly sweep does not mark everything stale forever and a
            // daily one does not stay quiet for a fortnight of failures.
            val interval by settingsStore.sweepInterval.collectAsState(initial = SweepInterval.DEFAULT)
            val rows = batteryRows(
                shades = shades.values,
                nowEpochMillis = System.currentTimeMillis(),
                staleAfterDays = staleAfterDays(interval),
            )
            // How many rows fit follows the widget's actual current size
            // rather than a size-blind constant, now that SizeMode.Responsive
            // means this composes again on every resize.
            val maxRows = maxRowsForHeight(LocalSize.current.height, rows.size)

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp)
                        .padding(12.dp)
                        .let { base ->
                            if (openApp == null) base else base.clickable(actionStartActivity(openApp))
                        },
                ) {
                    Text(
                        text = batterySummaryLine(rows),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )

                    Spacer(modifier = GlanceModifier.size(6.dp))

                    if (rows.isEmpty()) {
                        Text(
                            text = NO_BATTERY_SHADES_TEXT,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                            ),
                        )
                    } else {
                        val shown = batteryRowsToShow(rows, max = maxRows)

                        for (row in shown.rows) {
                            BatteryRowView(row)
                        }

                        hiddenRowsText(shown.hidden)?.let { overflow ->
                            Text(
                                text = overflow,
                                maxLines = 1,
                                modifier = GlanceModifier.padding(top = 4.dp),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The system entry point. Registered in this module's manifest. */
public class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}

@Composable
private fun BatteryRowView(row: BatteryRow) {
    // Low and unread are both emphasised; a stale reading is not, because the
    // number itself may still be fine and the age text already says so.
    val emphasise = row.low || row.percent == null

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
        )

        Box(
            modifier = GlanceModifier
                .background(
                    if (emphasise) {
                        GlanceTheme.colors.errorContainer
                    } else {
                        GlanceTheme.colors.secondaryContainer
                    },
                )
                .cornerRadius(8.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = batteryRowStatus(row),
                maxLines = 1,
                style = TextStyle(
                    color = if (emphasise) {
                        GlanceTheme.colors.onErrorContainer
                    } else {
                        GlanceTheme.colors.onSecondaryContainer
                    },
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/**
 * Redraws every placed battery widget.
 *
 * Public because `:app` calls it — `internal` is per Gradle module, so
 * `PowerViewApplication` could not see it otherwise.
 */
public suspend fun refreshBatteryWidgets(context: Context) {
    val widget = BatteryWidget()

    ignoringDisplayFailures {
        for (glanceId in GlanceAppWidgetManager(context).getGlanceIds(BatteryWidget::class.java)) {
            ignoringDisplayFailures { widget.update(context, glanceId) }
        }
    }
}
