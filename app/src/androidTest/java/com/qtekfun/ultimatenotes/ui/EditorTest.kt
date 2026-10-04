// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.ui.editor.EditorActions
import com.qtekfun.ultimatenotes.ui.editor.EditorContent
import com.qtekfun.ultimatenotes.ui.editor.EditorUiState
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled by `check`, run on a device: typing, Enter in a list, tapping a checkbox, undo. */
class EditorTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(
        note: String,
        readOnly: Boolean = false,
        actions: EditorActions = EditorActions()
    ): TextFieldState {
        val state = TextFieldState()
        state.setTextAndPlaceCursorAtEnd(note)
        compose.setContent {
            UltimateNotesTheme {
                EditorContent(
                    state = EditorUiState(loaded = true, readOnly = readOnly, category = "Work"),
                    text = state,
                    title = TextFieldState(),
                    isNew = false,
                    actions = actions
                )
            }
        }
        return state
    }

    @Test
    fun typingChangesTheMarkdownSource() {
        val state = show("")

        compose.onNodeWithContentDescription(text(R.string.editor_text_field))
            .performTextInput("# Plan")

        assertEquals("# Plan", state.text.toString())
    }

    @Test
    fun enterOnAListItemStartsTheNextOne() {
        val state = show("- milk")

        compose.onNodeWithContentDescription(text(R.string.editor_text_field))
            .performTextInput("\n")

        assertEquals("- milk\n- ", state.text.toString())
    }

    @Test
    fun tappingACheckboxTogglesExactlyOneCharacterAndUndoRestoresIt() {
        val state = show("- [ ] milk\n- [x] eggs")

        compose.onNodeWithContentDescription(text(R.string.editor_text_field))
            .performTouchInput { click(Offset(CHECKBOX_X, CHECKBOX_Y)) }

        assertEquals("- [x] milk\n- [x] eggs", state.text.toString())
        compose.onNodeWithContentDescription(text(R.string.undo)).performClick()
        assertEquals("- [ ] milk\n- [x] eggs", state.text.toString())
    }

    @Test
    fun theFormattingBarIsShownForEditableNotesOnly() {
        show("text")
        compose.onNodeWithContentDescription(text(R.string.format_bold)).assertIsDisplayed()
    }

    @Test
    fun readOnlyNotesShowTheNoticeAndHideTheBar() {
        show("text", readOnly = true)
        compose.onNodeWithContentDescription(text(R.string.format_bold)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.editor_readonly)).assertIsDisplayed()
    }

    private companion object {
        // Roughly where the first line's box glyph is: after the 16 dp padding and the "- ".
        const val CHECKBOX_X = 60f
        const val CHECKBOX_Y = 60f
    }
}
