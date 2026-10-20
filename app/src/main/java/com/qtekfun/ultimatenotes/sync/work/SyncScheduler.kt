// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import androidx.work.BackoffPolicy as WorkBackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.sync.SyncTrigger
import com.qtekfun.ultimatenotes.sync.queue.BackoffPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Schedules the sync workers. Both use unique work names, so triggers coalesce: a sync request
 * while one is already queued or running is dropped (an edit made during a run is skipped by the
 * engine and retried by the worker), unless the queued one is waiting to retry a failure. It is
 * also the real [SyncTrigger] for the UI.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settings: SettingsRepository,
    private val backoff: BackoffPolicy
) : SyncTrigger {
    /** Requests are decided one at a time, so two of them cannot both replace the pending sync. */
    private val requests = Mutex()

    /**
     * Syncs now, expedited when the system allows; use on app open and after a local save. A sync
     * that already failed and waits for its retry is replaced: that delay grows with every
     * failure, and opening the app or pulling to refresh must not wait for it.
     */
    suspend fun enqueueNow(manual: Boolean = false) {
        requests.withLock { enqueue(manual) }
    }

    private suspend fun enqueue(manual: Boolean) {
        val network = SyncWorkPlan.oneTimeNetwork(settings.settings.first(), manual)
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(
                WorkBackoffPolicy.EXPONENTIAL,
                backoff.initial.toJavaDuration()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val pending = workManager.getWorkInfosForUniqueWorkFlow(SyncWorkPlan.ONE_TIME_WORK).first()
        val policy = policyFor(pending.map { it.state to it.runAttemptCount })
        workManager.enqueueUniqueWork(SyncWorkPlan.ONE_TIME_WORK, policy, request).await()
    }

    /** Pull-to-refresh: enqueues (on any network) and returns once the attempt is over. */
    override suspend fun requestSync() {
        enqueueNow(manual = true)
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorkPlan.ONE_TIME_WORK)
            .first { infos -> infos.all { isSettled(it.state, it.runAttemptCount) } }
    }

    /** Keeps the periodic work in line with the settings, now and whenever they change. */
    fun keepPeriodicInSync(scope: CoroutineScope) {
        settings.settings
            .map { SyncWorkPlan.periodic(it) }
            .distinctUntilChanged()
            .onEach { applyPeriodic(it) }
            .launchIn(scope)
    }

    private suspend fun applyPeriodic(plan: PeriodicPlan?) {
        if (plan == null) {
            workManager.cancelUniqueWork(SyncWorkPlan.PERIODIC_WORK).await()
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(plan.interval)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(plan.network).build())
            .setBackoffCriteria(
                WorkBackoffPolicy.EXPONENTIAL,
                backoff.initial.toJavaDuration()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorkPlan.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        ).await()
    }

    companion object {
        /**
         * Whether a caller waiting on the work can stop: it finished, or it failed and was
         * rescheduled with a backoff (waiting for that would stall a pull-to-refresh).
         */
        fun isSettled(state: WorkInfo.State, runAttemptCount: Int): Boolean =
            state.isFinished || waitingToRetry(state, runAttemptCount)

        /**
         * How a new request treats the pending one, given its state and attempts so far: it
         * replaces a sync that already failed and waits for its retry, and keeps any other.
         */
        fun policyFor(pending: List<Pair<WorkInfo.State, Int>>): ExistingWorkPolicy =
            if (pending.any { (state, attempts) -> waitingToRetry(state, attempts) }) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }

        private fun waitingToRetry(state: WorkInfo.State, runAttemptCount: Int): Boolean =
            state == WorkInfo.State.ENQUEUED && runAttemptCount > 0
    }
}
