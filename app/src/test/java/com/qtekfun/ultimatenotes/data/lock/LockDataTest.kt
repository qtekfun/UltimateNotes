// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.lock

import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.lock.LockConfig
import com.qtekfun.ultimatenotes.domain.lock.LockTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LockDataTest {
    @Test
    fun `the store has no timestamp at first, keeps one and forgets it`() {
        val preferences = FakePreferences()
        val store = SharedPreferencesLockStore(preferences)
        assertNull(store.backgroundedAt)
        store.backgroundedAt = 0L
        assertEquals(0L, SharedPreferencesLockStore(preferences).backgroundedAt)
        store.backgroundedAt = 123L
        assertEquals(123L, store.backgroundedAt)
        store.backgroundedAt = null
        assertNull(store.backgroundedAt)
    }

    @Test
    fun `the lock settings default to off, immediate and no screenshot blocking`() {
        val settings = SettingsRepository(FakePreferences()).current
        assertFalse(settings.appLockEnabled)
        assertFalse(settings.secureWindow)
        assertEquals(LockTimeout.IMMEDIATELY, settings.lockTimeout)
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun `lock settings are stored and an unknown timeout falls back to the default`() {
        val preferences = FakePreferences()
        val repository = SettingsRepository(preferences)
        repository.setAppLockEnabled(true)
        repository.setLockTimeout(LockTimeout.FIFTEEN_MINUTES)
        repository.setSecureWindow(true)
        assertEquals(
            AppSettings(
                appLockEnabled = true,
                lockTimeout = LockTimeout.FIFTEEN_MINUTES,
                secureWindow = true
            ),
            repository.current
        )
        preferences.values["lock_timeout"] = "NOT_A_TIMEOUT"
        assertEquals(LockTimeout.IMMEDIATELY, repository.current.lockTimeout)
    }

    @Test
    fun `the config source reads the settings and can switch the lock off`() {
        val repository = SettingsRepository(FakePreferences())
        val source = SettingsLockConfigSource(repository)
        assertEquals(LockConfig(enabled = false), source.current())
        repository.setAppLockEnabled(true)
        repository.setLockTimeout(LockTimeout.ONE_MINUTE)
        assertEquals(LockConfig(true, LockTimeout.ONE_MINUTE), source.current())
        source.disable()
        assertEquals(LockConfig(false, LockTimeout.ONE_MINUTE), source.current())
    }
}
