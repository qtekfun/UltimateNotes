// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.folder

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.FolderCount
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveFoldersTest {
    @Test
    fun `combines the category counts with the favorites count and follows changes`() = runTest {
        val counts = MutableStateFlow(listOf(FolderCount("Work", 2)))
        val favorites = MutableStateFlow(emptyList<NoteEntity>())
        val dao = mockk<NoteDao> {
            every { observeFolderCounts() } returns counts
            every { observeFavorites() } returns favorites
        }

        ObserveFolders(dao)().test {
            assertEquals(FolderOverview.from(listOf(FolderCount("Work", 2)), 0), awaitItem())
            counts.value = listOf(FolderCount("Work", 3))
            assertEquals(3, awaitItem().total)
        }
    }
}
