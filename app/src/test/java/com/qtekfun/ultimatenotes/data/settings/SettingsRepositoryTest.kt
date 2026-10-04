// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.settings

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {
    private val preferences = FakePreferences()
    private val repository = SettingsRepository(preferences)

    @Test
    fun `defaults follow the system with wallpaper colors`() = runTest {
        repository.settings.test {
            assertEquals(
                AppSettings(ThemeMode.SYSTEM, amoled = false, dynamicColor = true),
                awaitItem()
            )
        }
    }

    @Test
    fun `every change is emitted and stored`() = runTest {
        repository.settings.test {
            awaitItem()
            repository.setTheme(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().theme)
            repository.setAmoled(true)
            assertEquals(true, awaitItem().amoled)
            repository.setDynamicColor(false)
            assertEquals(
                AppSettings(ThemeMode.DARK, amoled = true, dynamicColor = false),
                awaitItem()
            )
        }
    }

    @Test
    fun `the sort order defaults to modified and is stored`() = runTest {
        repository.settings.test {
            assertEquals(NoteSortOrder.MODIFIED, awaitItem().sortOrder)
            repository.setSortOrder(NoteSortOrder.TITLE)
            assertEquals(NoteSortOrder.TITLE, awaitItem().sortOrder)
        }
    }

    @Test
    fun `sync defaults to hourly on any network and changes are stored`() = runTest {
        repository.settings.test {
            val defaults = awaitItem()
            assertEquals(SyncInterval.HOUR, defaults.syncInterval)
            assertEquals(SyncNetwork.ANY, defaults.syncNetwork)
            repository.setSyncInterval(SyncInterval.SIX_HOURS)
            assertEquals(SyncInterval.SIX_HOURS, awaitItem().syncInterval)
            repository.setSyncNetwork(SyncNetwork.UNMETERED)
            assertEquals(SyncNetwork.UNMETERED, awaitItem().syncNetwork)
        }
    }

    @Test
    fun `an unknown stored sort order falls back to modified`() = runTest {
        preferences.values["sort_order"] = "RANDOM"
        repository.settings.test {
            assertEquals(NoteSortOrder.MODIFIED, awaitItem().sortOrder)
        }
    }

    @Test
    fun `unknown stored sync values fall back to the defaults`() = runTest {
        preferences.values["sync_interval"] = "EVERY_SECOND"
        preferences.values["sync_network"] = "CARRIER_PIGEON"
        repository.settings.test {
            val settings = awaitItem()
            assertEquals(SyncInterval.HOUR, settings.syncInterval)
            assertEquals(SyncNetwork.ANY, settings.syncNetwork)
        }
    }

    @Test
    fun `an unknown stored theme falls back to the system`() = runTest {
        preferences.values["theme"] = "SEPIA"
        repository.settings.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem().theme)
        }
    }
}
