// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.ui.main.MainContent
import com.qtekfun.ultimatenotes.ui.main.MainUiState
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled by `check`, run on a device: hamburger, folder drawer and the bottom bar. */
class MainContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = MainUiState(
        FolderSelection.All,
        FolderOverview(total = 3, noFolder = 1, folders = listOf(FolderNode("Work", 0, 2)))
    )

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun hamburgerOpensTheDrawerAndPickingAFolderSelectsIt() {
        var selected: FolderSelection? = null
        compose.setContent {
            UltimateNotesTheme {
                MainContent(state, { selected = it }, {}, {})
            }
        }

        compose.onNodeWithContentDescription(text(R.string.folders_open)).performClick()
        compose.onNodeWithText("Work").assertIsDisplayed().performClick()

        assertEquals(FolderSelection.Folder("Work"), selected)
    }

    @Test
    fun theBottomBarShowsSearchAndNewNote() {
        var newNotes = 0
        compose.setContent {
            UltimateNotesTheme {
                MainContent(state, {}, {}, { newNotes++ })
            }
        }

        compose.onNodeWithText(text(R.string.search_placeholder)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.note_new)).performClick()

        assertEquals(1, newNotes)
    }

    @Test
    fun settingsIsAtTheBottomOfTheDrawer() {
        var opened = false
        compose.setContent {
            UltimateNotesTheme {
                MainContent(state, {}, { opened = true }, {})
            }
        }

        compose.onNodeWithContentDescription(text(R.string.folders_open)).performClick()
        compose.onNodeWithText(text(R.string.folders_settings)).performClick()

        assertEquals(true, opened)
    }
}
