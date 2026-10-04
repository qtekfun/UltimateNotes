// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.folder

import com.qtekfun.ultimatenotes.data.local.model.FolderCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FolderOverviewTest {
    @Test
    fun `no notes gives an empty overview`() {
        assertEquals(FolderOverview(), FolderOverview.from(emptyList(), 0))
    }

    @Test
    fun `counts roll up into parents and the tree is in display order`() {
        val overview = FolderOverview.from(
            listOf(
                FolderCount("", 2),
                FolderCount("Work", 1),
                FolderCount("Work/Clients/Acme", 3),
                FolderCount("Home", 4)
            ),
            favorites = 5
        )

        assertEquals(10, overview.total)
        assertEquals(5, overview.favorites)
        assertEquals(2, overview.noFolder)
        assertEquals(
            listOf(
                FolderNode("Home", 0, 4),
                FolderNode("Work", 0, 4),
                FolderNode("Work/Clients", 1, 3),
                FolderNode("Work/Clients/Acme", 2, 3)
            ),
            overview.folders
        )
    }

    @Test
    fun `siblings sort ignoring case and stray slashes count as no folder`() {
        val overview = FolderOverview.from(
            listOf(FolderCount("/", 1), FolderCount("beta", 1), FolderCount("Alpha/", 1)),
            favorites = 0
        )

        assertEquals(1, overview.noFolder)
        assertEquals(listOf("Alpha", "beta"), overview.folders.map { it.path })
        assertEquals("Alpha", overview.folders.first().name)
    }

    @Test
    fun `categories differing only in case stay distinct and ordered`() {
        val overview = FolderOverview.from(listOf(FolderCount("a", 1), FolderCount("A", 2)), 0)

        assertEquals(listOf("A", "a"), overview.folders.map { it.path })
    }

    @Test
    fun `folder selection is named after its last segment`() {
        assertEquals("Acme", FolderSelection.Folder("Work/Acme").name)
        assertEquals("Work", FolderSelection.Folder("Work").name)
    }
}
