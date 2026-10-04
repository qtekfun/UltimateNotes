// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.list.NoteGroup
import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import com.qtekfun.ultimatenotes.domain.list.NoteSection
import com.qtekfun.ultimatenotes.ui.main.ListActions
import com.qtekfun.ultimatenotes.ui.main.MainContent
import com.qtekfun.ultimatenotes.ui.main.MainUiState
import com.qtekfun.ultimatenotes.ui.main.PendingDeletion
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled by `check`, run on a device: sections, rows, long press and the undo snackbar. */
class NoteListTest {
    @get:Rule
    val compose = createComposeRule()

    private val plan = NoteListItem(1, "Plan", "first steps", "Work", true, Instant.now())
    private val shopping = NoteListItem(2, "Shopping", "milk eggs", "", false, Instant.now())

    private val state = MainUiState(
        selection = FolderSelection.All,
        groups = listOf(
            NoteGroup(NoteSection.Pinned, listOf(plan)),
            NoteGroup(NoteSection.Today, listOf(shopping))
        ),
        loaded = true
    )

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(state: MainUiState, actions: ListActions = ListActions()) =
        compose.setContent {
            UltimateNotesTheme { MainContent(state, {}, {}, {}, actions = actions) }
        }

    @Test
    fun sectionsRowsAndFolderNamesAreShown() {
        show(state)

        compose.onNodeWithText(text(R.string.section_pinned)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.section_today)).assertIsDisplayed()
        compose.onNodeWithText("Plan").assertIsDisplayed()
        compose.onNodeWithText("Work").assertIsDisplayed()
        compose.onNodeWithText("Shopping").assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensTheNote() {
        var opened = 0L
        show(state, ListActions(onOpenNote = { opened = it }))

        compose.onNodeWithText("Shopping").performClick()

        assertEquals(2L, opened)
    }

    @Test
    fun longPressStartsSelection() {
        var picked = 0L
        show(state, ListActions(onToggleSelected = { picked = it }))

        compose.onNodeWithText("Shopping").performTouchInput { longClick() }

        assertEquals(2L, picked)
    }

    @Test
    fun anEmptyFolderShowsItsMessage() {
        show(MainUiState(selection = FolderSelection.Favorites, loaded = true))

        compose.onNodeWithText(text(R.string.list_empty_favorites)).assertIsDisplayed()
    }

    @Test
    fun theUndoSnackbarBringsBackADeletion() {
        var undone = false
        show(
            state.copy(pendingDeletion = PendingDeletion(1, 1)),
            ListActions(onUndoDelete = { undone = true })
        )

        compose.onNodeWithText(text(R.string.undo)).performClick()

        assertEquals(true, undone)
    }

    @Test
    fun selectionModeShowsTheBulkActions() {
        var deleted = false
        show(
            state.copy(selectedIds = setOf(1L)),
            ListActions(onDeleteSelected = { deleted = true })
        )

        compose.onNodeWithContentDescription(text(R.string.select_move)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.note_delete)).performClick()

        assertEquals(true, deleted)
    }
}
