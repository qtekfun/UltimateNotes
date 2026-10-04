// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackoffPolicyTest {
    private val policy = BackoffPolicy(initial = 10.seconds, max = 1.minutes, maxAttempts = 5)

    @Test
    fun `the delay doubles from the initial value and stops at the maximum`() {
        assertEquals(
            listOf(10.seconds, 20.seconds, 40.seconds, 1.minutes, 1.minutes),
            (1..5).map(policy::delayFor)
        )
    }

    @Test
    fun `a huge attempt number does not overflow`() {
        assertEquals(1.minutes, policy.delayFor(Int.MAX_VALUE))
        assertEquals(1.hours, BackoffPolicy().delayFor(Int.MAX_VALUE))
    }

    @Test
    fun `defaults start at thirty seconds`() {
        assertEquals(30.seconds, BackoffPolicy().delayFor(1))
    }

    @Test
    fun `attempts start at one`() {
        assertThrows<IllegalArgumentException> { policy.delayFor(0) }
    }

    @Test
    fun `an invalid policy is rejected`() {
        assertThrows<IllegalArgumentException> { BackoffPolicy(initial = 0.seconds) }
        assertThrows<IllegalArgumentException> {
            BackoffPolicy(initial = 2.minutes, max = 1.minutes)
        }
        assertThrows<IllegalArgumentException> { BackoffPolicy(maxAttempts = 0) }
    }

    @Test
    fun `transient errors are retried with the backoff delay`() {
        val transient = listOf(
            ApiError.Offline,
            ApiError.Server(503),
            ApiError.InvalidResponse,
            ApiError.Conflict(null)
        )

        transient.forEach {
            assertEquals(
                RetryDecision.RetryAfter(20.seconds),
                policy.decide(it, attempt = 2),
                "$it"
            )
        }
    }

    @Test
    fun `errors that need the user are never retried`() {
        val permanent = listOf(
            ApiError.Unauthorized,
            ApiError.NotesAppMissing,
            ApiError.NotFound,
            ApiError.Forbidden,
            ApiError.UnsupportedApi
        )

        permanent.forEach {
            assertEquals(RetryDecision.GiveUp, policy.decide(it, attempt = 1), "$it")
        }
    }

    @Test
    fun `it gives up once the attempts are used`() {
        assertEquals(
            RetryDecision.RetryAfter(40.seconds),
            policy.decide(ApiError.Offline, attempt = 3)
        )
        assertEquals(
            RetryDecision.RetryAfter(1.minutes),
            policy.decide(ApiError.Offline, attempt = 4)
        )
        assertEquals(RetryDecision.GiveUp, policy.decide(ApiError.Offline, attempt = 5))
    }
}
