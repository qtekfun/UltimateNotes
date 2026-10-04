// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import com.qtekfun.ultimatenotes.sync.queue.BackoffPolicy
import com.qtekfun.ultimatenotes.sync.queue.RetryDecision
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import java.time.Clock

/** Runs one sync; implemented by the engine provider, faked in tests. */
fun interface SyncSource {
    suspend fun sync(): SyncResult
}

/** What a worker should do with a finished run. */
enum class RunOutcome { DONE, RETRY, STOP }

/**
 * The decision logic of the workers, free of WorkManager: runs the sync, publishes the status and
 * says whether to retry. Retries (within the [BackoffPolicy] limits) when notes were skipped or the
 * error is transient; never on errors that need the user (Unauthorized, NotesAppMissing,
 * UnsupportedApi...), which stay in the status as an error until a run succeeds.
 */
class SyncRunner(
    private val source: SyncSource,
    private val status: SyncStatusStore,
    private val clock: Clock,
    private val policy: BackoffPolicy = BackoffPolicy()
) {
    /** [attempt] is 1 for the first try of a piece of work, 2 for its first retry... */
    suspend fun run(attempt: Int): RunOutcome {
        status.markSyncing()
        return when (val result = source.sync()) {
            is SyncResult.Success -> {
                status.markSynced(clock.instant())
                if (result.report.skipped > 0 && attempt < policy.maxAttempts) {
                    RunOutcome.RETRY
                } else {
                    RunOutcome.DONE
                }
            }

            is SyncResult.Failed -> {
                status.markError(SyncErrorKind.of(result.error))
                when (policy.decide(result.error, attempt)) {
                    is RetryDecision.RetryAfter -> RunOutcome.RETRY
                    RetryDecision.GiveUp -> RunOutcome.STOP
                }
            }
        }
    }
}
