// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.folder.ObserveFolders
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val overview =
        MutableStateFlow(FolderOverview(total = 3, folders = listOf(FolderNode("Work", 0, 3))))
    private val observeFolders = mockk<ObserveFolders> { every { this@mockk() } returns overview }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts on all notes with the folder overview`() = runTest {
        MainViewModel(observeFolders).state.test {
            assertEquals(MainUiState(FolderSelection.All, overview.value), expectMostRecentItem())
        }
    }

    @Test
    fun `selecting a folder changes what is shown`() = runTest {
        val model = MainViewModel(observeFolders)
        model.state.test {
            expectMostRecentItem()
            model.select(FolderSelection.Folder("Work"))
            assertEquals(FolderSelection.Folder("Work"), awaitItem().selection)
        }
    }

    @Test
    fun `folder changes reach the drawer`() = runTest {
        MainViewModel(observeFolders).state.test {
            expectMostRecentItem()
            overview.value = FolderOverview(total = 4, favorites = 1)
            assertEquals(4, awaitItem().folders.total)
        }
    }
}
