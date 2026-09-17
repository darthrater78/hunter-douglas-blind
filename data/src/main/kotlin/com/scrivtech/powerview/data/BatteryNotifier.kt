package com.scrivtech.powerview.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts the low-battery notification for the weekly sweep (build order step 10,
 * spec §2.5).
 *
 * One notification for the whole sweep, not one per shade. A home can have a
 * dozen shades and they were all bought at the same time, so they reach the
 * threshold at roughly the same time too — a notification each would arrive as
 * a burst that says one thing.
 */
public class BatteryNotifier(private val context: Context) {

    /**
     * Notifies that [lowShades] are at or below [LOW_BATTERY_PERCENT]. No-op
     * when the list is empty or the notification permission is not held.
     *
     * Silently doing nothing without the permission is deliberate: the caller
     * is a background worker with no way to prompt, and the sweep's real work
     * (recording the readings) has already happened and is visible in the app.
     */
    // NotificationManagerCompat.notify carries @RequiresPermission(POST_NOTIFICATIONS).
    // hasNotificationPermission() below is that check, but lint cannot follow it
    // through a helper. Suppressed rather than left to trip lintVitalRelease,
    // which runs on every assembleRelease — the same gate that caught the
    // WorkManager initializer conflict.
    @SuppressLint("MissingPermission")
    public fun notifyLowBatteries(lowShades: List<LowBatteryShade>) {
        if (lowShades.isEmpty()) return
        if (!hasNotificationPermission()) return

        ensureChannel()

        val title = if (lowShades.size == 1) {
            "${lowShades.single().label} has a low battery"
        } else {
            "${lowShades.size} shades have low batteries"
        }

        val body = lowShades.sortedBy { it.percent }
            .joinToString(", ") { "${it.label} ${it.percent}%" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // TODO: replace with a real notification icon alongside the launcher
            // icon (see the app manifest's TODO). A platform drawable is used
            // rather than the launcher icon because a notification small icon
            // must be a flat silhouette, and the launcher icon is not one.
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .apply {
                // Reaching the app from the notification without :data having to
                // know anything about :app or its activities.
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.let { intent ->
                        setContentIntent(
                            android.app.PendingIntent.getActivity(
                                context,
                                0,
                                intent,
                                android.app.PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                    }
            }
            .build()

        // A fixed id, so a later sweep replaces the previous notification
        // instead of stacking a second one saying almost the same thing.
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Low battery",
            // DEFAULT, not HIGH: a shade at 20% has weeks left. This is
            // something to notice, not something to interrupt for.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Warns when a shade's battery is running low."
        }

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    public companion object {
        public const val CHANNEL_ID: String = "shade_battery"
        private const val NOTIFICATION_ID = 1001
    }
}

/** One shade worth warning about, as [BatterySweepWorker] found it. */
public data class LowBatteryShade(
    public val macAddress: String,
    public val label: String,
    public val percent: Int,
)
