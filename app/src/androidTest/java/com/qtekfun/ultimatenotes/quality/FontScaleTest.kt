// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.util.Log
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.auth.AuthModule
import com.qtekfun.ultimatenotes.di.DatabaseModule
import com.qtekfun.ultimatenotes.di.SettingsModule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T17b, system font scale (SPEC §11). Run it with the system font scale set from the host and the
 * scale given as `-e expectedFontScale 2.0`, so the test cannot pass by running at 1.0 by mistake
 * (`scripts/t17b-font-scale.sh`). The main screen, the folder drawer, the editor, the search and
 * the settings must keep their controls on screen, reachable and not on top of each other, and
 * the controls' labels must not be cut off. PNGs of each screen go to `Android/media/<package>/t17b/`.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class, AuthModule::class, SettingsModule::class)
class FontScaleTest : DeviceTestBase() {
    private val checks by lazy { LayoutChecks(compose) }
    private val scale get() = context.resources.configuration.fontScale
    private val tag get() = "font${(scale * PERCENT).toInt()}"

    @Before
    fun requireTheExpectedScale() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedFontScale")
        if (expected != null) assertEquals(expected.toFloat(), scale, SCALE_TOLERANCE)
        Log.i(T17B_TAG, "font scale in use: $scale")
        seedDemoNotes()
    }

    private fun control(description: Int) = string(description) to byDescription(description)

    @Test
    fun mainScreenAndFloatingSearchBar() {
        launch()
        awaitText(string(R.string.section_pinned))
        shoot("${tag}_1_main")
        val controls = arrayOf(
            control(R.string.folders_open),
            control(R.string.sync_now),
            control(R.string.list_more),
            control(R.string.search_field),
            control(R.string.note_new)
        )
        checks.assertInsideWindow("main", *controls)
        checks.assertNoOverlap("main", *controls)
        controls.forEach { it.second.assertIsDisplayed() }
        // The bar's search capsule and the new-note button sit side by side and keep a real size.
        val search = byDescription(R.string.search_field).fetchSemanticsNode().boundsInRoot
        val newNote = byDescription(R.string.note_new).fetchSemanticsNode().boundsInRoot
        assertTrue("the search capsule is too small: $search", search.width > MIN_CAPSULE_PX)
        assertTrue("the new-note button is too small: $newNote", newNote.width > MIN_BUTTON_PX)
    }

    @Test
    fun folderDrawerKeepsItsFooterReachable() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        shoot("${tag}_2_drawer")
        val all = clickableText(R.string.folder_all)
        val settings = clickableText(R.string.folders_settings)
        all.assertIsDisplayed()
        settings.assertIsDisplayed()
        checks.assertInsideWindow("drawer", "all" to all, "settings" to settings)
        checks.assertNoOverlap("drawer", "all" to all, "settings" to settings)
        // The folder rows scroll: the last one can be brought into view.
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(string(R.string.folder_all))))
            .onFirst().performScrollToNode(hasText("Recipes"))
        val drawerRight = settings.fetchSemanticsNode().boundsInRoot.right
        val reached = compose.onAllNodes(hasText("Recipes")).fetchSemanticsNodes()
            .any { it.boundsInRoot.right <= drawerRight && it.boundsInRoot.height > 0 }
        assertTrue("the last drawer folder was not brought into view", reached)
        // The labels of the rows are not cut off.
        val rows = compose.onAllNodes(hasClickAction())
        assertTrue("drawer labels cut off: ${checks.overflowing(rows)}", checks.overflowing(rows).isEmpty())
    }

    @Test
    fun editorKeepsTitleBodyAndFormattingBarUsable() {
        launch()
        awaitText(string(R.string.section_pinned))
        compose.onNodeWithText("Lisbon trip plan").performClick()
        awaitDescription(string(R.string.editor_text_field))
        shoot("${tag}_3_editor")
        val bar = arrayOf(
            control(R.string.settings_back),
            control(R.string.undo),
            control(R.string.editor_redo),
            control(R.string.editor_more),
            control(R.string.editor_title_field),
            control(R.string.editor_text_field)
        )
        checks.assertInsideWindow("editor", *bar)
        checks.assertNoOverlap("editor", *bar.take(4).toTypedArray())
        // Every formatting button can be scrolled into view and is a full-size touch target.
        for (id in FORMAT_BUTTONS) {
            byDescription(id).performScrollTo().assertIsDisplayed()
        }
        // With the keyboard open, a usable stretch of the text is still visible.
        byDescription(R.string.editor_text_field).performClick()
        await { keyboardVisible() }
        settle()
        shoot("${tag}_4_editor_keyboard")
        val text = byDescription(R.string.editor_text_field).fetchSemanticsNode().boundsInRoot
        val density = activity().resources.displayMetrics.density
        assertTrue(
            "the text area is only ${text.height / density} dp tall with the keyboard open",
            text.height / density >= MIN_TEXT_DP
        )
        checks.assertInsideWindow("editor+keyboard", control(R.string.editor_text_field))
        checks.assertInsideWindow(
            "editor+keyboard bar",
            control(R.string.format_toolbar)
        )
    }

    @Test
    fun searchKeepsTheFieldAboveTheKeyboardWithResults() {
        launch()
        awaitText(string(R.string.section_pinned))
        byDescription(R.string.search_field).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        awaitText("Lemon cake")
        await { keyboardVisible() }
        settle()
        shoot("${tag}_5_search")
        val field = compose.onNode(hasSetTextAction())
        val close = byDescription(R.string.search_close)
        field.assertIsDisplayed()
        close.assertIsDisplayed()
        checks.assertInsideWindow("search", "field" to field, "close" to close)
        checks.assertNoOverlap("search", "field" to field, "close" to close)
        compose.onNodeWithText("Lemon cake").assertIsDisplayed()
    }

    @Test
    fun settingsKeepEveryControlReachable() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        clickableText(R.string.folders_settings).performClick()
        awaitText(string(R.string.settings_title))
        settle()
        shoot("${tag}_6_settings_top")
        val clickable = compose.onAllNodes(hasClickAction())
        val count = clickable.fetchSemanticsNodes().size
        assertTrue("settings has no controls", count > MIN_SETTINGS_CONTROLS)
        for (index in 0 until count) {
            val control: SemanticsNodeInteraction = clickable[index]
            // The top bar's back button is not inside the scrolling column.
            runCatching { control.performScrollTo() }
            control.assertIsDisplayed()
        }
        shoot("${tag}_7_settings_bottom")
        val cut = checks.overflowing(compose.onAllNodes(hasClickAction()))
        assertTrue("settings labels cut off: $cut", cut.isEmpty())
        byDescription(R.string.settings_back).assertIsDisplayed()
    }

    @Test
    fun landscapeEditorWithKeyboardStaysUsable() {
        launch()
        awaitText(string(R.string.section_pinned))
        compose.onNodeWithText("Lisbon trip plan").performClick()
        awaitDescription(string(R.string.editor_text_field))
        rotate(landscape = true)
        awaitDescription(string(R.string.editor_text_field))
        byDescription(R.string.editor_text_field).performClick()
        await { keyboardVisible() }
        settle()
        shoot("${tag}_8_editor_landscape_keyboard")
        val text = byDescription(R.string.editor_text_field).fetchSemanticsNode().boundsInRoot
        val density = activity().resources.displayMetrics.density
        Log.i(T17B_TAG, "landscape editor with keyboard: text area ${text.height / density} dp")
        assertTrue(
            "the text area is only ${text.height / density} dp tall in landscape with the keyboard",
            text.height / density >= MIN_TEXT_DP
        )
        checks.assertInsideWindow("landscape editor", control(R.string.editor_text_field))
        rotate(landscape = false)
    }

    private companion object {
        const val PERCENT = 100
        const val SCALE_TOLERANCE = 0.01f
        const val MIN_CAPSULE_PX = 150f
        const val MIN_BUTTON_PX = 100f
        const val MIN_TEXT_DP = 48f
        const val MIN_SETTINGS_CONTROLS = 3
        val FORMAT_BUTTONS = listOf(
            R.string.format_bold,
            R.string.format_italic,
            R.string.format_strike,
            R.string.format_heading,
            R.string.format_bullet,
            R.string.format_numbered,
            R.string.format_checklist,
            R.string.format_quote,
            R.string.format_link,
            R.string.format_code
        )
    }
}
