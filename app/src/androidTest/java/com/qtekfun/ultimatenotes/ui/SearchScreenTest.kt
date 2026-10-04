// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compiled by `check`, run on a device: typing, results, scope chips, empty states and closing. */
class SearchScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Like MainActivity (edge to edge, `adjustResize`): the screen pads itself by the keyboard
     * insets. In a plain test activity the window instead pans up when the (auto-focused) field
     * opens the keyboard, which moves the chips and the results off screen and fails
     * `assertIsDisplayed`.
     */
    @Suppress("DEPRECATION") // the same flag MainActivity's manifest sets
    @Before
    fun behaveLikeTheMainActivity() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }
    }

    /** The string in the device's language: the tests must pass in English and in Spanish. */
    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

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
        // Checked before the click: the screen under test holds a fixed (empty) query, so the field
        // reports the reset to "" when it loses focus.
        assertEquals("caf", typed)
        compose.onNodeWithText("Food").assertIsDisplayed()
        compose.onNodeWithText("Café menu", substring = true).performClick()

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
        compose.onNodeWithText(text(R.string.search_scope_folder, "Work")).performClick()

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
