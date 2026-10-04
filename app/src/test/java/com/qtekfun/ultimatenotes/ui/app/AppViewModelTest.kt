// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.app

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.ui.theme.ThemeOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val storage = FakeAccountStorage()
    private val cipher = FakeCipher()
    private val session = AccountSession(storage, cipher, Dispatchers.Unconfined)
    private val preferences = FakePreferences()
    private val server =
        (ServerUrl.parse("https://cloud.example.com") as ServerUrl.ParseResult.Valid).url

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppViewModel(session, SettingsRepository(preferences))

    @Test
    fun `without a stored account the app starts on the login screen`() = runTest {
        assertEquals(AppDestination.LOGIN, viewModel().destination.value)
    }

    @Test
    fun `a stored account is restored and goes straight to the main screen`() = runTest {
        session.signIn(server, Credentials("ana", "secret"))
        val coldStart = AccountSession(storage, cipher, Dispatchers.Unconfined)

        val model = AppViewModel(coldStart, SettingsRepository(preferences))

        assertEquals(AppDestination.MAIN, model.destination.value)
    }

    @Test
    fun `signing in and out moves between the screens`() = runTest {
        val model = viewModel()
        model.destination.test {
            assertEquals(AppDestination.LOGIN, awaitItem())
            session.signIn(server, Credentials("ana", "secret"))
            assertEquals(AppDestination.MAIN, awaitItem())
            session.signOut()
            assertEquals(AppDestination.LOGIN, awaitItem())
        }
    }

    @Test
    fun `the theme follows the settings`() = runTest {
        val repository = SettingsRepository(preferences)
        val model = AppViewModel(session, repository)
        assertEquals(ThemeOptions(), model.theme.value)

        repository.setTheme(ThemeMode.DARK)
        repository.setAmoled(true)
        repository.setDynamicColor(false)

        assertEquals(
            ThemeOptions(ThemeMode.DARK, amoled = true, dynamicColor = false),
            model.theme.value
        )
    }
}
