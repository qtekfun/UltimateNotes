// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Whether to try a failed sync again, and when. */
sealed interface RetryDecision {
    data class RetryAfter(val delay: Duration) : RetryDecision

    data object GiveUp : RetryDecision
}

/**
 * Exponential backoff as a pure function (the workers of T08 apply it): the delay doubles on each
 * attempt from [initial] up to [max]. Errors that need the user (credentials, missing app, old
 * server, read-only note) are never retried, and neither is anything past [maxAttempts].
 */
data class BackoffPolicy(
    val initial: Duration = 30.seconds,
    val max: Duration = 1.hours,
    val maxAttempts: Int = 8
) {
    init {
        require(initial.isPositive() && max >= initial && maxAttempts > 0) { "invalid backoff" }
    }

    /** Delay before retry number [attempt] (1 = first retry). */
    fun delayFor(attempt: Int): Duration {
        require(attempt >= 1) { "attempt starts at 1" }
        val doublings = (attempt - 1).coerceAtMost(MAX_DOUBLINGS)
        return (initial * (1 shl doublings)).coerceAtMost(max)
    }

    fun decide(error: ApiError, attempt: Int): RetryDecision =
        if (isTransient(error) && attempt < maxAttempts) {
            RetryDecision.RetryAfter(delayFor(attempt))
        } else {
            RetryDecision.GiveUp
        }

    private fun isTransient(error: ApiError): Boolean = when (error) {
        ApiError.Offline, is ApiError.Server, ApiError.InvalidResponse, is ApiError.Conflict -> true

        ApiError.Unauthorized, ApiError.NotesAppMissing, ApiError.NotFound,
        ApiError.Forbidden, ApiError.UnsupportedApi -> false
    }

    private companion object {
        const val MAX_DOUBLINGS = 20
    }
}
