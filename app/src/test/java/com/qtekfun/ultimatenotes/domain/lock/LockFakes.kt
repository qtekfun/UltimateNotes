// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand, forwards or backwards. */
class MutableClock(var millis: Long = 1_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(millis)

    fun advance(ms: Long) {
        millis += ms
    }
}

class FakeLockStore(override var backgroundedAt: Long? = null) : LockStore

class FakeConfigSource(var config: LockConfig = LockConfig()) : LockConfigSource {
    var disabled = 0

    override fun current(): LockConfig = config

    override fun disable() {
        disabled++
        config = config.copy(enabled = false)
    }
}
