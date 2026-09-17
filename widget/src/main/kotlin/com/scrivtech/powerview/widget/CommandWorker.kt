package com.scrivtech.powerview.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.scrivtech.powerview.data.ActionStore
import kotlinx.coroutines.flow.first

/**
 * Runs one [com.scrivtech.powerview.data.ShadeAction] via [ActionRunner], per
 * the execution path in spec §3.3. Expedited, one-shot `WorkManager` work —
 * enqueued by the (not-yet-built, build order step 9) Glance widget click
 * callback, and by any other command surface once it exists.
 *
 * TODO(build order step 9): start a foreground service
 * (`foregroundServiceType="connectedDevice"`) here if the action will run
 * more than a moment — required for reliable execution when the app has been
 * backgrounded for a while, per spec §3.3 step 2.
 */
public class CommandWorker(
    context: Context,
    params: WorkerParameters,
    private val actionStore: ActionStore,
    private val actionRunner: ActionRunner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val actionId = inputData.getString(KEY_ACTION_ID) ?: return Result.failure()
        val action = actionStore.actions.first().firstOrNull { it.id == actionId }
            ?: return Result.failure(workDataOf(KEY_ERROR to "unknown action id: $actionId"))

        return when (val outcome = actionRunner.run(action)) {
            ActionResult.Success -> Result.success()
            is ActionResult.Failed -> Result.failure(
                workDataOf(KEY_FAILED_MACS to outcome.failedMacAddresses.toTypedArray()),
            )
            ActionResult.Pending -> Result.retry() // should not be observed as a terminal state from ActionRunner.run
        }
    }

    public companion object {
        public const val KEY_ACTION_ID: String = "action_id"
        public const val KEY_ERROR: String = "error"
        public const val KEY_FAILED_MACS: String = "failed_macs"

        public fun inputData(actionId: String) = workDataOf(KEY_ACTION_ID to actionId)
    }

    /**
     * Constructor-injects [ActionStore]/[ActionRunner] since `CommandWorker`
     * has no no-arg constructor for the default `WorkManager` factory to use.
     * Register an instance of this via
     * `Configuration.Builder().setWorkerFactory(...)` in the Application's
     * `WorkManager` configuration — wiring deferred to `:app`, build order
     * step 9.
     */
    public class Factory(
        private val actionStore: ActionStore,
        private val actionRunnerProvider: (Context) -> ActionRunner,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? {
            if (workerClassName != CommandWorker::class.java.name) return null
            return CommandWorker(appContext, workerParameters, actionStore, actionRunnerProvider(appContext))
        }
    }
}
