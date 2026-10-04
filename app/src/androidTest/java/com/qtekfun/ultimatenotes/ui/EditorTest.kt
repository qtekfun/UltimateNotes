// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.ui.editor.EditorActions
import com.qtekfun.ultimatenotes.ui.editor.EditorContent
import com.qtekfun.ultimatenotes.ui.editor.EditorUiState
import com.qtekfun.ultimatenotes.ui.editor.TEXT_PADDING
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

    private val title = TextFieldState()

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
                    title = title,
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

        val field = compose.onNodeWithContentDescription(text(R.string.editor_text_field))
        val box = centerOfGlyph(field, state.text.indexOf('['))
        field.performTouchInput { click(box) }

        assertEquals("- [x] milk\n- [x] eggs", state.text.toString())
        compose.onNodeWithContentDescription(text(R.string.undo)).performClick()
        assertEquals("- [ ] milk\n- [x] eggs", state.text.toString())
    }

    /**
     * Where the glyph at [offset] is drawn, in the node's own coordinates, asked of the field's
     * text layout (the same one the editor hit-tests with), so it does not depend on screen size,
     * density or font scale. The field's semantics node is the area inside [TEXT_PADDING] (checked
     * on a device: node and pointer input are both 1328 px wide on a 1440 px screen), so the
     * layout's coordinates are the node's own and no padding is added.
     */
    private fun centerOfGlyph(field: SemanticsNodeInteraction, offset: Int): Offset {
        val layouts = mutableListOf<TextLayoutResult>()
        val read = field.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        check(read.action?.invoke(layouts) == true && layouts.isNotEmpty()) {
            "the field exposes no text layout"
        }
        return layouts.first().getBoundingBox(offset).center
    }

    @Test
    fun theTitleLineShowsItsHintUntilSomethingIsTyped() {
        show("body")
        compose.onNodeWithText(text(R.string.editor_title_hint)).assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.editor_title_field))
            .performTextInput("Plan")

        compose.onNodeWithText(text(R.string.editor_title_hint)).assertDoesNotExist()
    }

    @Test
    fun typingInTheTitleLeavesTheBodyAlone() {
        val body = show("body")

        compose.onNodeWithContentDescription(text(R.string.editor_title_field))
            .performTextInput("Plan")

        assertEquals("Plan", title.text.toString())
        assertEquals("body", body.text.toString())
    }

    @Test
    fun enterInTheTitleMovesTheFocusToTheBody() {
        show("body")
        val titleField = compose.onNodeWithContentDescription(text(R.string.editor_title_field))
        titleField.performClick()
        titleField.assertIsFocused()

        titleField.performImeAction()

        compose.onNodeWithContentDescription(text(R.string.editor_text_field)).assertIsFocused()
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
}
