// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.auth.AuthModule
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.di.DatabaseModule
import com.qtekfun.ultimatenotes.di.SettingsModule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T17b, rotation (SPEC §11): landscape and back while editing, with the keyboard open, with the
 * drawer open, in a search and in settings. The activity is rotated through a requested
 * orientation, which is the same configuration change (and recreation) a real rotation causes.
 * Nothing the user typed may be lost, and the screen must come back as it was.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class, AuthModule::class, SettingsModule::class)
class RotationTest : DeviceTestBase() {
    @Before
    fun seed() = seedDemoNotes()

    private fun field(description: Int): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(string(description)) and hasSetTextAction())

    private fun textOf(description: Int): String =
        field(description).fetchSemanticsNode().config[SemanticsProperties.InputText].text

    private fun selectionOf(description: Int): TextRange =
        field(description).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun typeAtEnd(description: Int, text: String) {
        val node = field(description)
        node.performTextInputSelection(TextRange(textOf(description).length))
        node.performTextInput(text)
        compose.waitForIdle()
    }

    private fun notes(): List<NoteEntity> = runBlocking { database.noteSyncDao().getAll() }

    private fun noteTitled(title: String): NoteEntity = notes().single { it.title == title }

    private fun openNote(title: String) {
        awaitText(title)
        compose.onNodeWithText(title).performClick()
        awaitDescription(string(R.string.editor_text_field))
        settle()
    }

    /** Back closes the keyboard first when it is open, then the editor. */
    private fun leaveEditor() {
        repeat(MAX_BACKS) {
            if (hasNodesWithDescription(string(R.string.editor_text_field))) pressBack()
        }
        awaitText(string(R.string.section_pinned))
    }

    private fun awaitSaved(title: String, content: String) = await {
        notes().any { it.title == title && it.content == content }
    }

    @Test
    fun editorTextTitleAndSelectionSurviveRotationBothWays() {
        launch()
        openNote("Fluffy pancakes")
        val original = noteTitled("Fluffy pancakes").content
        field(R.string.editor_text_field).performTextInputSelection(TextRange(3))
        field(R.string.editor_text_field).performTextInput("ABC")
        typeAtEnd(R.string.editor_title_field, " v2")
        val body = textOf(R.string.editor_text_field)
        assertEquals(original.substring(0, 3) + "ABC" + original.substring(3), body)
        field(R.string.editor_text_field).performTextInputSelection(TextRange(6, 10))
        val selection = selectionOf(R.string.editor_text_field)
        assertEquals(TextRange(6, 10), selection)

        rotate(landscape = true)
        awaitDescription(string(R.string.editor_text_field))
        shoot("rotation_1_editor_landscape")
        assertEquals(body, textOf(R.string.editor_text_field))
        assertEquals("Fluffy pancakes v2", textOf(R.string.editor_title_field))
        // The caret stays where the selection ended; the highlighted range itself collapses (the
        // text field restarts its input session when the activity is recreated).
        assertEquals(
            "caret after rotating to landscape",
            selection.end,
            selectionOf(R.string.editor_text_field).end
        )

        rotate(landscape = false)
        awaitDescription(string(R.string.editor_text_field))
        assertEquals(body, textOf(R.string.editor_text_field))
        assertEquals("Fluffy pancakes v2", textOf(R.string.editor_title_field))
        assertEquals(
            "caret after rotating back",
            selection.end,
            selectionOf(R.string.editor_text_field).end
        )

        // The autosave (1 s after the last change) and the final save reach the database.
        awaitSaved("Fluffy pancakes v2", body)
        leaveEditor()
        assertEquals(body, noteTitled("Fluffy pancakes v2").content)
    }

    @Test
    fun typingAcrossRotationsWithTheKeyboardOpenLosesNothing() {
        launch()
        awaitText(string(R.string.section_pinned))
        byDescription(R.string.note_new).performClick()
        awaitDescription(string(R.string.editor_text_field))
        await { keyboardVisible() }
        typeAtEnd(R.string.editor_title_field, "Rotation note")
        typeAtEnd(R.string.editor_text_field, "first ")
        Log.i(T17B_TAG, "keyboard open before rotating: ${keyboardVisible()}")

        // Rotated before the 1 s autosave could run: the text must still arrive.
        rotate(landscape = true)
        awaitDescription(string(R.string.editor_text_field))
        Log.i(T17B_TAG, "keyboard open after rotating to landscape: ${keyboardVisible()}")
        assertEquals("first ", textOf(R.string.editor_text_field))
        typeAtEnd(R.string.editor_text_field, "second ")
        shoot("rotation_2_typing_landscape")

        rotate(landscape = false)
        awaitDescription(string(R.string.editor_text_field))
        assertEquals("first second ", textOf(R.string.editor_text_field))
        typeAtEnd(R.string.editor_text_field, "third")

        // Several quick rotations in a row, text typed in between.
        repeat(RAPID_ROTATIONS) { turn ->
            rotate(landscape = turn % 2 == 0)
            awaitDescription(string(R.string.editor_text_field))
            typeAtEnd(R.string.editor_text_field, ".")
        }
        val expected = "first second third" + ".".repeat(RAPID_ROTATIONS)
        assertEquals(expected, textOf(R.string.editor_text_field))
        assertEquals("Rotation note", textOf(R.string.editor_title_field))
        // Leave in portrait, like the user would.
        rotate(landscape = false)
        awaitSaved("Rotation note", expected)
        leaveEditor()
        val saved = notes().filter { it.title == "Rotation note" }
        assertEquals("exactly one note was created", 1, saved.size)
        assertEquals(expected, saved.single().content)
    }

    @Test
    fun theSelectedFolderAndTheOpenDrawerSurviveRotation() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        clickableText(R.string.folder_favorites).performClick()
        settle()
        awaitText("Lisbon trip plan")
        assertFalse("Favorites hides the other notes", hasNodesWithText("Fluffy pancakes"))

        rotate(landscape = true)
        shoot("rotation_3_favorites_landscape")
        assertTrue(hasNodesWithText("Lisbon trip plan"))
        assertFalse("the folder is kept: Fluffy pancakes must stay hidden", hasNodesWithText("Fluffy pancakes"))

        // Rotating with the drawer open keeps it open.
        openDrawer()
        rotate(landscape = false)
        awaitText(string(R.string.folders_settings))
        shoot("rotation_4_drawer_portrait")
        assertTrue("the drawer was closed by the rotation", hasNodesWithText(string(R.string.folders_settings)))
        closeDrawerByChoosingAllNotes()
        awaitText("Fluffy pancakes")
    }

    @Test
    fun theSearchQueryAndResultsSurviveRotation() {
        launch()
        awaitText(string(R.string.section_pinned))
        byDescription(R.string.search_field).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        awaitText("Lemon cake")

        rotate(landscape = true)
        awaitText("Lemon cake")
        shoot("rotation_5_search_landscape")
        assertEquals("lemon", compose.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.InputText].text)

        rotate(landscape = false)
        awaitText("Lemon cake")
        assertEquals("lemon", compose.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.InputText].text)

        // Open a result, rotate inside the editor, and come back to the same search.
        compose.onNodeWithText("Lemon cake").performClick()
        awaitDescription(string(R.string.editor_text_field))
        rotate(landscape = true)
        awaitDescription(string(R.string.editor_text_field))
        assertEquals("Lemon cake", textOf(R.string.editor_title_field))
        rotate(landscape = false)
        pressBack()
        awaitText("Lemon cake")
        assertEquals("lemon", compose.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.InputText].text)
    }

    @Test
    fun settingsSurviveRotation() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        clickableText(R.string.folders_settings).performClick()
        awaitText(string(R.string.settings_title))
        rotate(landscape = true)
        assertTrue(hasNodesWithText(string(R.string.settings_title)))
        shoot("rotation_6_settings_landscape")
        rotate(landscape = false)
        assertTrue(hasNodesWithText(string(R.string.settings_title)))
        pressBack()
        awaitText(string(R.string.section_pinned))
    }

    @Test
    fun anActivityRecreationInTheEditorKeepsTheText() {
        launch()
        openNote("Grocery list")
        typeAtEnd(R.string.editor_text_field, " END")
        val body = textOf(R.string.editor_text_field)
        requireNotNull(scenario).recreate()
        compose.waitForIdle()
        awaitDescription(string(R.string.editor_text_field))
        assertEquals(body, textOf(R.string.editor_text_field))
        awaitSaved("Grocery list", body)
    }

    private companion object {
        const val RAPID_ROTATIONS = 6
        const val MAX_BACKS = 3
    }
}
