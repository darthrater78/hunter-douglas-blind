package com.scrivtech.powerview.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scrivtech.powerview.ble.ShadeGattClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The weekly battery sweep (build order step 10, spec §2.5).
 *
 * Reading a battery means connecting to the shade, and connecting costs the
 * shade power — the thing being measured. That shapes everything here: the
 * sweep runs weekly rather than daily, skips shades marked mains-powered, and
 * gives up immediately when it could not possibly succeed rather than working
 * through a dozen doomed connections.
 *
 * Uses WorkManager's default worker factory rather than being added to
 * [CommandWorker]'s. `CommandWorker.Factory` returns null for any other worker
 * class, which makes WorkManager fall back to reflection on the standard
 * `(Context, WorkerParameters)` constructor — so this class builds its own
 * collaborators from the application context. They are cheap: [ShadeStore]
 * resolves to the same underlying DataStore however many wrappers exist.
 */
public class BatterySweepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Every read would fail the same way without this, each after a full
        // connection attempt. Fail fast instead, and without retrying: the fix
        // is a user granting a permission, which no amount of backoff produces.
        if (!ShadeGattClient.hasConnectPermission(applicationContext)) return Result.success()

        val shadeStore = ShadeStore(applicationContext)
        val batteryReader = BatteryReader(applicationContext, shadeStore)

        val candidates = shadeStore.shades.first().values.filterNot { it.mainsPowered }
        if (candidates.isEmpty()) return Result.success()

        val low = mutableListOf<LowBatteryShade>()

        for (shade in candidates) {
            // Sequential on purpose. The BLE stack serializes connections
            // anyway, and a burst of parallel attempts mostly produces
            // failures that look like the shades are out of range.
            when (val result = batteryReader.read(shade.macAddress)) {
                is BatteryReadResult.Success -> {
                    if (batteryLevelOf(result.percent) == BatteryLevel.LOW) {
                        low += LowBatteryShade(
                            macAddress = shade.macAddress,
                            label = shade.label,
                            percent = result.percent,
                        )
                    }
                }

                // Out of range, asleep, or no battery characteristic. None of
                // these is worth retrying or reporting: the shade will be swept
                // again next week, and a notification about a failed *reading*
                // is noise about the app rather than news about the shades.
                BatteryReadResult.NotSupported -> Unit
                is BatteryReadResult.Failed -> Unit
            }
        }

        BatteryNotifier(applicationContext).notifyLowBatteries(low)

        // Success even when some reads failed. Retrying the whole sweep because
        // one shade was out of range would reconnect to every other shade too,
        // spending their power to re-learn what was already recorded.
        return Result.success()
    }

    public companion object {
        private const val WORK_NAME = "battery-sweep"
        private const val INTERVAL_DAYS = 7L

        /**
         * Schedules the sweep if it is not already scheduled. Safe to call on
         * every app start — [ExistingPeriodicWorkPolicy.KEEP] means an existing
         * schedule is left alone, so the interval is not restarted each launch
         * (which, with a weekly period, could stop it ever running).
         */
        public fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<BatterySweepWorker>(
                INTERVAL_DAYS,
                TimeUnit.DAYS,
            ).setConstraints(
                Constraints.Builder()
                    // Not a reason to drain the user's phone: this is
                    // housekeeping, and a week is a wide enough window to wait
                    // for a better moment.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
