package com.scrivtech.powerview.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scrivtech.powerview.ble.ShadeGattClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The periodic battery sweep (build order step 10, spec §2.5).
 *
 * Reading a battery means connecting to the shade, and connecting costs the
 * shade power — the thing being measured. That shapes everything here: the
 * sweep skips shades marked mains-powered, works through the rest one at a
 * time, and gives up immediately when it could not possibly succeed rather
 * than working through a dozen doomed connections.
 *
 * How often it runs is a user setting ([SweepInterval], stored in
 * [SettingsStore]) rather than a constant, because the cost of being wrong
 * runs both ways: sweeping daily across a dozen shades is itself a drain,
 * while a month between sweeps lets a shade die a fortnight before anything
 * says so. The default is the weekly period it had before it was
 * configurable.
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
        val settingsStore = SettingsStore(applicationContext)

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

        // The sweep runs and the readings are recorded either way; this is the
        // one place a "no notifications" preference can take effect, since
        // BatteryNotifier itself has no view of settings.
        if (settingsStore.notificationsEnabled.first()) {
            BatteryNotifier(applicationContext).notifyLowBatteries(low)
        }

        // Success even when some reads failed. Retrying the whole sweep because
        // one shade was out of range would reconnect to every other shade too,
        // spending their power to re-learn what was already recorded.
        return Result.success()
    }

    public companion object {
        private const val WORK_NAME = "battery-sweep"
        private const val MANUAL_WORK_NAME = "battery-sweep-now"

        /**
         * Schedules the sweep at [interval] if it is not already scheduled.
         *
         * For app start. [ExistingPeriodicWorkPolicy.KEEP] leaves an existing
         * schedule alone, so the period is not restarted on every launch —
         * which, at a weekly or monthly period, could stop it ever running on
         * a phone that gets opened daily.
         */
        public fun ensureScheduled(context: Context, interval: SweepInterval) {
            schedule(context, interval, ExistingPeriodicWorkPolicy.KEEP)
        }

        /**
         * Applies a *changed* [interval], starting the new period now.
         *
         * [ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE] rather than
         * `UPDATE`: someone who has just switched from monthly to daily means
         * "start sweeping daily", not "sweep daily once the month is up", and
         * `UPDATE` can keep the original next-run time.
         */
        public fun reschedule(context: Context, interval: SweepInterval) {
            schedule(context, interval, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }

        /**
         * Runs one sweep immediately, outside the schedule.
         *
         * This is what makes [SweepInterval.OFF] a real choice rather than a
         * way to never see a battery reading again: with the periodic job
         * cancelled, this is how the user asks for one. Unique and
         * [ExistingWorkPolicy.KEEP], so an impatient second tap does not
         * connect to every shade twice.
         */
        public fun sweepNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BatterySweepWorker>().build(),
            )
        }

        private fun schedule(
            context: Context,
            interval: SweepInterval,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val workManager = WorkManager.getInstance(context)

            // Cancel rather than schedule something that never fires: a
            // cancelled unique work name also means ensureScheduled can put
            // it back cleanly if the user turns it on again.
            val days = interval.days ?: run {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<BatterySweepWorker>(
                days,
                TimeUnit.DAYS,
            ).setConstraints(
                Constraints.Builder()
                    // Not a reason to drain the user's phone: this is
                    // housekeeping, and even the shortest interval here is a
                    // wide enough window to wait for a better moment.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            ).build()

            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }
}
