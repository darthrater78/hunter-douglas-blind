package com.scrivtech.powerview.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.scrivtech.powerview.data.ActionResult
import com.scrivtech.powerview.data.ActionRunner
import com.scrivtech.powerview.data.ActionStore
import kotlinx.coroutines.flow.first

/**
 * Runs one [com.scrivtech.powerview.data.ShadeAction] via [ActionRunner], per
 * the execution path in spec §3.3. One-shot `WorkManager` work, enqueued
 * through [CommandDispatch] by the Glance widget's tap callback and by any
 * other command surface.
 *
 * See [CommandDispatch] for why this is *not* expedited work, and what would
 * have to be declared before it could be.
 *
 * Reporting the outcome is part of the job, not an extra: a widget button
 * left showing "Sending…" forever is worse than one that says it failed, so
 * every terminal path here goes through [finish].
 */
public class CommandWorker(
    context: Context,
    params: WorkerParameters,
    private val actionStore: ActionStore,
    private val actionRunner: ActionRunner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // No action id means nothing to report against, so there is no widget
        // state to settle either — this is the one path that does not finish().
        val actionId = inputData.getString(KEY_ACTION_ID) ?: return Result.failure()

        val action = actionStore.actions.first().firstOrNull { it.id == actionId }
            ?: return finish(
                actionId = actionId,
                succeeded = false,
                result = Result.failure(workDataOf(KEY_ERROR to "unknown action id: $actionId")),
            )

        return when (val outcome = actionRunner.run(action)) {
            ActionResult.Success -> finish(actionId, succeeded = true, result = Result.success())

            is ActionResult.Failed -> finish(
                actionId = actionId,
                succeeded = false,
                result = Result.failure(
                    workDataOf(KEY_FAILED_MACS to outcome.failedMacAddresses.toTypedArray()),
                ),
            )

            // Should not be observed as a terminal state from ActionRunner.run.
            // Left pending on purpose: a retry is still in flight, and marking
            // the widget idle or failed now would contradict the next attempt.
            ActionResult.Pending -> Result.retry()
        }
    }

    /**
     * Settles the widgets showing this action, then returns [result].
     *
     * A success clears back to [SlotRun.IDLE] rather than showing a tick: the
     * widget cannot verify that the shade actually moved — only that the frame
     * was acknowledged — and a confirmation mark would claim more than
     * `ActionRunner` knows. The in-app screens carry that nuance in words.
     */
    private suspend fun finish(actionId: String, succeeded: Boolean, result: Result): Result {
        WidgetStatus.mark(
            context = applicationContext,
            actionId = actionId,
            run = if (succeeded) SlotRun.IDLE else SlotRun.FAILED,
        )
        return result
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
     * `WorkManager` configuration — `PowerViewApplication` does this. Note
     * that WorkManager's automatic initializer must stay removed in the
     * manifest for that registration to be seen at all.
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
