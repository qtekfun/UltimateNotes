// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.ui.main.MainContent
import com.qtekfun.ultimatenotes.ui.main.MainUiState
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compiled by `check`, run on a device (T17b): the main screen's controls can be found by a screen
 * reader (they have a content description) and are big enough to hit (48 dp, CLAUDE.md).
 */
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun show() {
        compose.setContent {
            UltimateNotesTheme {
                MainContent(
                    state = MainUiState(FolderSelection.All, FolderOverview(total = 0)),
                    onSelect = {},
                    onOpenSettings = {},
                    onNewNote = {}
                )
            }
        }
    }

    private fun assertReachable(description: Int) {
        compose.onNodeWithContentDescription(text(description))
            .assertHasClickAction()
            .assertHeightIsAtLeast(MIN_TARGET)
            .assertWidthIsAtLeast(MIN_TARGET)
    }

    @Test
    fun theSyncButtonIsLabelledAndBigEnough() = assertReachable(R.string.sync_now)

    @Test
    fun theNewNoteButtonIsLabelledAndBigEnough() = assertReachable(R.string.note_new)

    @Test
    fun theSearchBarIsLabelledAndBigEnough() = assertReachable(R.string.search_field)

    @Test
    fun theHamburgerIsLabelledAndBigEnough() = assertReachable(R.string.folders_open)

    private companion object {
        val MIN_TARGET = 48.dp
    }
}
