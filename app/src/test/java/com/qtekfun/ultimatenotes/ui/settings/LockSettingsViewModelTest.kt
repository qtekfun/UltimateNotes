// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.lock.LockCapability
import com.qtekfun.ultimatenotes.domain.lock.LockCapabilityChecker
import com.qtekfun.ultimatenotes.domain.lock.LockTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockSettingsViewModelTest {
    private val repository = SettingsRepository(FakePreferences())
    private var capability = LockCapability.AVAILABLE
    private val checker = LockCapabilityChecker { capability }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the lock turns on only when the device can authenticate`() = runTest {
        capability = LockCapability.NOT_ENROLLED
        val model = LockSettingsViewModel(repository, checker)
        model.state.test {
            assertFalse(expectMostRecentItem().canToggleLock)
            model.setLockEnabled(true)
            assertFalse(repository.current.appLockEnabled)
            capability = LockCapability.AVAILABLE
            model.setLockEnabled(true)
            val state = expectMostRecentItem()
            assertTrue(state.settings.appLockEnabled)
            assertTrue(state.canToggleLock)
        }
    }

    @Test
    fun `it can always be turned off, even if the screen lock was removed`() = runTest {
        val model = LockSettingsViewModel(repository, checker)
        model.setLockEnabled(true)
        capability = LockCapability.NOT_ENROLLED
        model.state.test {
            assertTrue(expectMostRecentItem().canToggleLock)
            model.setLockEnabled(false)
            assertFalse(expectMostRecentItem().settings.appLockEnabled)
        }
    }

    @Test
    fun `refreshing picks up a screen lock set up meanwhile`() = runTest {
        capability = LockCapability.UNSUPPORTED
        val model = LockSettingsViewModel(repository, checker)
        model.state.test {
            assertEquals(LockCapability.UNSUPPORTED, expectMostRecentItem().capability)
            capability = LockCapability.AVAILABLE
            model.refreshCapability()
            assertEquals(LockCapability.AVAILABLE, expectMostRecentItem().capability)
        }
    }

    @Test
    fun `timeout and screenshot blocking are stored`() = runTest {
        val model = LockSettingsViewModel(repository, checker)
        model.setTimeout(LockTimeout.FIVE_MINUTES)
        model.setSecureWindow(true)
        val settings = repository.current
        assertEquals(LockTimeout.FIVE_MINUTES, settings.lockTimeout)
        assertTrue(settings.secureWindow)
    }

    @Test
    fun `the explanation depends on why the lock is unavailable`() {
        fun reason(c: LockCapability, on: Boolean = false) = unavailableReason(
            LockSettingsState(
                SettingsRepository(FakePreferences()).current.copy(appLockEnabled = on),
                c
            )
        )
        assertNull(reason(LockCapability.AVAILABLE))
        assertEquals(
            com.qtekfun.ultimatenotes.R.string.settings_app_lock_not_enrolled,
            reason(LockCapability.NOT_ENROLLED)
        )
        assertEquals(
            com.qtekfun.ultimatenotes.R.string.settings_app_lock_unsupported,
            reason(LockCapability.UNSUPPORTED)
        )
        assertNull(reason(LockCapability.UNSUPPORTED, on = true))
    }

    @Test
    fun `every timeout has its own label`() {
        assertEquals(LockTimeout.entries.size, LockTimeout.entries.map(::timeoutLabel).toSet().size)
    }
}
