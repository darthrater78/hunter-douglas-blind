package com.scrivtech.powerview.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
 */
public class BatteryWidget : GlanceAppWidget() {

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
                        val shown = batteryRowsToShow(rows)

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
