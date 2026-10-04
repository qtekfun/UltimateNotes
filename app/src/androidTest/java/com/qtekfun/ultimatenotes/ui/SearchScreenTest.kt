// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.search.Highlighted
import com.qtekfun.ultimatenotes.domain.search.SearchResult
import com.qtekfun.ultimatenotes.ui.search.SearchActions
import com.qtekfun.ultimatenotes.ui.search.SearchScope
import com.qtekfun.ultimatenotes.ui.search.SearchScreen
import com.qtekfun.ultimatenotes.ui.search.SearchStatus
import com.qtekfun.ultimatenotes.ui.search.SearchUiState
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled by `check`, run on a device: typing, results, scope chips, empty states and closing. */
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val result = SearchResult(
        localId = 7,
        title = Highlighted("Café menu", listOf(TextRange(0, 4))),
        snippet = Highlighted("the best café in town", listOf(TextRange(9, 13))),
        category = "Food",
        favorite = false,
        modified = Instant.parse("2026-10-01T10:00:00Z")
    )

    @Test
    fun typingReportsTheQueryAndATappedResultOpensTheNote() {
        var typed = ""
        var opened = 0L
        val state = SearchUiState(
            active = true,
            status = SearchStatus.RESULTS,
            results = listOf(result)
        )
        compose.setContent {
            UltimateNotesTheme {
                SearchScreen(
                    state,
                    SearchActions(onQueryChange = { typed = it }, onOpenNote = { opened = it })
                )
            }
        }

        compose.onNodeWithText(text(R.string.search_placeholder)).performTextInput("caf")
        compose.onNodeWithText("Food").assertIsDisplayed()
        compose.onNodeWithText("Café menu", substring = true).performClick()

        assertEquals("caf", typed)
        assertEquals(7L, opened)
    }

    @Test
    fun scopeChipsAppearInsideAFolderAndReportTheChoice() {
        var scope: SearchScope? = null
        compose.setContent {
            UltimateNotesTheme {
                SearchScreen(
                    SearchUiState(active = true, scopeFolder = FolderSelection.Folder("Work")),
                    SearchActions(onScopeChange = { scope = it })
                )
            }
        }

        compose.onNodeWithText(text(R.string.folder_all)).assertIsDisplayed()
        compose.onNodeWithText("In Work").performClick()

        assertEquals(SearchScope.FOLDER, scope)
    }

    @Test
    fun emptyStatesAndTheCloseButton() {
        var closed = false
        compose.setContent {
            UltimateNotesTheme {
                SearchScreen(
                    SearchUiState(active = true, query = "zzz", status = SearchStatus.NO_RESULTS),
                    SearchActions(onClose = { closed = true })
                )
            }
        }

        compose.onNodeWithText(text(R.string.search_no_results)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.search_close)).performClick()

        assertEquals(true, closed)
    }
}
