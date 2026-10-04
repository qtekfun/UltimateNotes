// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LockPolicyTest {
    private val clock = MutableClock(10_000_000L)
    private val policy = LockPolicy(clock)

    private fun on(timeout: LockTimeout) = LockConfig(enabled = true, timeout = timeout)

    @Test
    fun `a disabled lock never locks, whatever the history`() {
        val off = LockConfig(enabled = false, timeout = LockTimeout.IMMEDIATELY)
        assertFalse(policy.shouldLock(off, null))
        assertFalse(policy.shouldLock(off, 0L))
        assertFalse(policy.shouldLock(off, clock.millis + 1))
    }

    @Test
    fun `the lock is off by default and the default timeout is immediate`() {
        assertEquals(LockConfig(enabled = false, timeout = LockTimeout.IMMEDIATELY), LockConfig())
    }

    @Test
    fun `the timeouts are 0, 1, 5 and 15 minutes`() {
        assertEquals(
            listOf(0L, 60_000L, 300_000L, 900_000L),
            LockTimeout.entries.map { it.millis }
        )
    }

    @Test
    fun `no timestamp means a restored process and locks`() {
        LockTimeout.entries.forEach { assertTrue(policy.shouldLock(on(it), null)) }
    }

    @Test
    fun `immediately locks even with no time elapsed`() {
        assertTrue(policy.shouldLock(on(LockTimeout.IMMEDIATELY), clock.millis))
    }

    @ParameterizedTest
    @EnumSource(LockTimeout::class, mode = EnumSource.Mode.EXCLUDE, names = ["IMMEDIATELY"])
    fun `stays unlocked one millisecond before the timeout and locks exactly at it`(
        timeout: LockTimeout
    ) {
        val away = clock.millis
        clock.advance(timeout.millis - 1)
        assertFalse(policy.shouldLock(on(timeout), away))
        clock.advance(1)
        assertTrue(policy.shouldLock(on(timeout), away))
        clock.advance(1)
        assertTrue(policy.shouldLock(on(timeout), away))
    }

    @Test
    fun `a quick return inside the timeout does not lock`() {
        val away = clock.millis
        clock.advance(5_000)
        assertFalse(policy.shouldLock(on(LockTimeout.ONE_MINUTE), away))
    }

    @Test
    fun `a clock moved backwards locks instead of extending the grace period`() {
        val away = clock.millis
        clock.advance(-1)
        assertTrue(policy.shouldLock(on(LockTimeout.FIFTEEN_MINUTES), away))
        clock.advance(-3_600_000)
        assertTrue(policy.shouldLock(on(LockTimeout.FIFTEEN_MINUTES), away))
    }

    @Test
    fun `a clock jumped far forward locks`() {
        val away = clock.millis
        clock.advance(365L * 24 * 3_600_000)
        assertTrue(policy.shouldLock(on(LockTimeout.FIFTEEN_MINUTES), away))
    }

    @Test
    fun `a timestamp at the epoch is a plain old timestamp, not a special value`() {
        clock.millis = 0L
        assertFalse(policy.shouldLock(on(LockTimeout.ONE_MINUTE), 0L))
        clock.advance(60_000)
        assertTrue(policy.shouldLock(on(LockTimeout.ONE_MINUTE), 0L))
    }

    @Test
    fun `now reads the injected clock`() {
        assertEquals(10_000_000L, policy.now())
        clock.advance(7)
        assertEquals(10_000_007L, policy.now())
    }
}
