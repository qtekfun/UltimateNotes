// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.auth.Account
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.domain.auth.Logout
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val session = AccountSession(FakeAccountStorage(), FakeCipher(), Dispatchers.Unconfined)
    private val repository = SettingsRepository(FakePreferences())
    private val logout = mockk<Logout>()
    private val server =
        (ServerUrl.parse("https://cloud.example.com") as ServerUrl.ParseResult.Valid).url

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the settings and the account, and applies changes`() = runTest {
        session.signIn(server, Credentials("ana", "secret"))
        val model = SettingsViewModel(repository, session, logout)
        model.state.test {
            assertEquals(AppSettings(), expectMostRecentItem().settings)
            model.setTheme(ThemeMode.DARK)
            model.setAmoled(true)
            model.setDynamicColor(false)
            model.setSortOrder(NoteSortOrder.TITLE)
            val state = expectMostRecentItem()
            assertEquals(
                AppSettings(
                    ThemeMode.DARK,
                    amoled = true,
                    dynamicColor = false,
                    sortOrder = NoteSortOrder.TITLE
                ),
                state.settings
            )
            assertEquals(Account("https://cloud.example.com/", "ana"), state.account)
        }
    }

    @Test
    fun `logging out runs once while busy and then is available again`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { logout() } coAnswers { gate.await() }
        val model = SettingsViewModel(repository, session, logout)
        model.state.test {
            assertFalse(expectMostRecentItem().loggingOut)
            model.logOut()
            assertEquals(true, awaitItem().loggingOut)
            model.logOut()
            gate.complete(Unit)
            assertFalse(awaitItem().loggingOut)
        }
        coVerify(exactly = 1) { logout() }
    }
}
