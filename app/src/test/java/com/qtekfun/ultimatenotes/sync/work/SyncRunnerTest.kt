// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.sync.TEST_CLOCK
import com.qtekfun.ultimatenotes.sync.queue.BackoffPolicy
import com.qtekfun.ultimatenotes.sync.queue.SyncReport
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FakeStatusStore : SyncStatusStore {
    private val state = MutableStateFlow(SyncStatus())
    override val status: StateFlow<SyncStatus> = state
    val history = mutableListOf<SyncPhase>()

    override fun markSyncing() {
        history += SyncPhase.Syncing
        state.value = state.value.copy(phase = SyncPhase.Syncing)
    }

    override fun markSynced(at: Instant) {
        state.value = SyncStatus(SyncPhase.Idle, at)
    }

    override fun markError(kind: SyncErrorKind) {
        state.value = state.value.copy(phase = SyncPhase.Error(kind))
    }
}

class SyncRunnerTest {
    private val status = FakeStatusStore()

    private fun run(result: SyncResult, attempt: Int = 1): RunOutcome = runBlocking {
        SyncRunner({ result }, status, TEST_CLOCK, BackoffPolicy(maxAttempts = 3)).run(attempt)
    }

    @Test
    fun `a clean run is done and records when it synced`() {
        assertEquals(RunOutcome.DONE, run(SyncResult.Success(SyncReport(pulled = 2))))

        assertEquals(SyncStatus(SyncPhase.Idle, TEST_CLOCK.instant()), status.status.value)
        assertEquals(listOf<SyncPhase>(SyncPhase.Syncing), status.history)
    }

    @Test
    fun `skipped notes are retried`() {
        assertEquals(RunOutcome.RETRY, run(SyncResult.Success(SyncReport(skipped = 1))))
        assertEquals(SyncPhase.Idle, status.status.value.phase)
    }

    @Test
    fun `skipped notes are not retried forever`() {
        assertEquals(RunOutcome.DONE, run(SyncResult.Success(SyncReport(skipped = 1)), attempt = 3))
    }

    @Test
    fun `a transient error is retried and shown`() {
        val result = SyncResult.Failed(ApiError.Offline, SyncReport())

        assertEquals(RunOutcome.RETRY, run(result))
        assertEquals(SyncPhase.Error(SyncErrorKind.OFFLINE), status.status.value.phase)
    }

    @Test
    fun `a transient error stops retrying after the last attempt`() {
        assertEquals(
            RunOutcome.STOP,
            run(SyncResult.Failed(ApiError.Server(503), SyncReport()), attempt = 3)
        )
        assertEquals(SyncPhase.Error(SyncErrorKind.SERVER), status.status.value.phase)
    }

    @Test
    fun `errors that need the user are never retried`() {
        val expected = mapOf(
            ApiError.Unauthorized to SyncErrorKind.UNAUTHORIZED,
            ApiError.NotesAppMissing to SyncErrorKind.NOTES_APP_MISSING,
            ApiError.UnsupportedApi to SyncErrorKind.UNSUPPORTED_API
        )
        expected.forEach { (error, kind) ->
            assertEquals(RunOutcome.STOP, run(SyncResult.Failed(error, SyncReport())))
            assertEquals(SyncPhase.Error(kind), status.status.value.phase)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `a later success clears the error and keeps the timestamp`(attempt: Int) {
        run(SyncResult.Failed(ApiError.Unauthorized, SyncReport()), attempt)
        run(SyncResult.Success(SyncReport()), attempt)

        assertEquals(SyncStatus(SyncPhase.Idle, TEST_CLOCK.instant()), status.status.value)
    }

    @Test
    fun `every error maps to a kind`() {
        assertEquals(SyncErrorKind.SERVER, SyncErrorKind.of(ApiError.Conflict(null)))
        assertEquals(SyncErrorKind.SERVER, SyncErrorKind.of(ApiError.InvalidResponse))
        assertEquals(SyncErrorKind.SERVER, SyncErrorKind.of(ApiError.NotFound))
        assertEquals(SyncErrorKind.SERVER, SyncErrorKind.of(ApiError.Forbidden))
    }
}
