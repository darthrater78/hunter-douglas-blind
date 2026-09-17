package com.scrivtech.powerview.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * The one place a home-screen surface turns a tap into work. The Glance
 * widget uses it, and the Quick Settings tile (build order step 11) will use
 * the same call.
 *
 * **Not expedited work, deliberately.** The obvious choice for a tap is
 * `setExpedited`, and the original TODO here said so. But below API 31
 * `WorkManager` satisfies an expedited request by promoting the worker to a
 * foreground service, which requires [androidx.work.ListenableWorker.getForegroundInfo]
 * — whose default implementation throws — plus `FOREGROUND_SERVICE` and
 * `FOREGROUND_SERVICE_CONNECTED_DEVICE` in the manifest and a declared
 * service. With `minSdk = 26` that is a crash on Android 8 through 11, not a
 * degraded experience. Ordinary one-shot work starts promptly enough for a
 * command that then spends seconds on BLE anyway. Revisit together with the
 * foreground service, not before.
 *
 * The unique work name is per action and the policy is [ExistingWorkPolicy.KEEP],
 * which makes a second tap on an already-queued action a no-op rather than a
 * duplicate connect/disconnect cycle to the same shades. That is the last of
 * three debounce layers: the widget hides the tap target while a slot is
 * pending, [RunActionCallback] re-checks the stored state behind it, and this
 * catches whatever still gets through — including a tap on a *different*
 * widget instance showing the same action.
 */
internal object CommandDispatch {

    fun enqueue(context: Context, actionId: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(actionId),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CommandWorker>()
                .setInputData(CommandWorker.inputData(actionId))
                .build(),
        )
    }

    private fun uniqueWorkName(actionId: String) = "command-$actionId"
}
