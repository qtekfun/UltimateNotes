// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.util.Log
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
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
 * T17b, accessibility without a screen reader. Two checks over the main flows:
 *
 * 1. Google's Accessibility Test Framework (Apache-2.0, the engine behind Espresso and Compose
 *    accessibility checks) over the real window: touch target size, missing labels, contrast,
 *    duplicate clickable bounds and so on. Every ERROR fails the test.
 * 2. The semantics tree: every control has a spoken label, state is announced (selected, toggled,
 *    disabled) and the checklist has a custom action.
 *
 * This is NOT a TalkBack run: nothing here proves how the speech sounds or that gestures work.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class, AuthModule::class, SettingsModule::class)
class AccessibilityAuditTest : DeviceTestBase() {
    // checkAndReturnResults reports instead of throwing; the test decides what fails.
    private val validator = AccessibilityValidator().setRunChecksFromRootView(true)

    @Before
    fun seed() = seedDemoNotes()

    /** Runs the framework on the current window; logs everything, returns the errors. */
    private fun audit(screen: String): List<String> {
        settle()
        var results: List<AccessibilityViewCheckResult> = emptyList()
        instrumentation.runOnMainSync {
            results = validator.checkAndReturnResults(activity().window.decorView)
        }
        val summary = results.groupingBy { it.type }.eachCount()
        Log.i(T17B_TAG, "a11y $screen: ${results.size} results $summary")
        results.filter { it.type == AccessibilityCheckResultType.NOT_RUN }
            .groupingBy { "${it.sourceCheckClass.simpleName}: ${it.message.toString().take(90)}" }
            .eachCount()
            .forEach { (what, count) -> Log.i(T17B_TAG, "a11y $screen NOT_RUN x$count $what") }
        results.filter {
            it.type == AccessibilityCheckResultType.ERROR ||
                it.type == AccessibilityCheckResultType.WARNING
        }.forEach {
            Log.i(
                T17B_TAG,
                "a11y $screen ${it.type} ${it.sourceCheckClass.simpleName}: ${it.message}"
            )
        }
        return results.filter { it.type == AccessibilityCheckResultType.ERROR }
            .map { "${it.sourceCheckClass.simpleName}: ${it.message}" }
    }

    private fun assertClean(screen: String) {
        val errors = audit(screen)
        assertTrue("accessibility errors on $screen:\n" + errors.joinToString("\n"), errors.isEmpty())
    }

    private fun clickables(): List<SemanticsNode> =
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    private fun SemanticsNode.label(): String {
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        return description.orEmpty().ifBlank { text.orEmpty() }
    }

    /** Every control with a click action has something a screen reader can say. */
    private fun assertAllControlsAreLabelled(screen: String) {
        val unlabelled = clickables().filter { it.label().isBlank() }
        assertTrue(
            "$screen: ${unlabelled.size} controls without a label: " +
                unlabelled.joinToString { it.boundsInRoot.toString() },
            unlabelled.isEmpty()
        )
    }

    @Test
    fun mainScreenPassesTheFramework() {
        launch()
        awaitText(string(R.string.section_pinned))
        assertClean("main")
        assertAllControlsAreLabelled("main")
    }

    @Test
    fun drawerPassesTheFramework() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        assertClean("drawer")
        assertAllControlsAreLabelled("drawer")
        // The current folder is announced as selected.
        val all = clickables().first { it.label().contains(string(R.string.folder_all)) }
        assertEquals(true, all.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun editorPassesTheFrameworkAndTheChecklistHasACustomAction() {
        launch()
        awaitText(string(R.string.section_pinned))
        compose.onNodeWithText("Grocery list").performClick()
        awaitDescription(string(R.string.editor_text_field))
        assertClean("editor")
        assertAllControlsAreLabelled("editor")
        // Checklist: checkboxes are text in the field, so the field offers the toggle as an action.
        val field = compose.onNode(
            androidx.compose.ui.test.hasContentDescription(string(R.string.editor_text_field)) and
                hasSetTextAction()
        ).fetchSemanticsNode()
        val actions = field.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertTrue(
            "the note text has no checklist action: ${actions.map { it.label }}",
            actions.any { it.label == string(R.string.editor_toggle_checkbox) }
        )
        // Buttons are announced as buttons.
        val bold = clickables().first { it.label() == string(R.string.format_bold) }
        assertEquals(Role.Button, bold.config.getOrNull(SemanticsProperties.Role))
        // Disabled controls say so (nothing to undo yet).
        val redo = clickables().first { it.label() == string(R.string.editor_redo) }
        assertTrue(
            "redo is not announced as disabled",
            redo.config.contains(SemanticsProperties.Disabled)
        )
    }

    @Test
    fun searchPassesTheFramework() {
        launch()
        awaitText(string(R.string.section_pinned))
        byDescription(R.string.search_field).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        awaitText("Lemon cake")
        assertClean("search")
        assertAllControlsAreLabelled("search")
    }

    @Test
    fun settingsPassTheFrameworkAndStateIsAnnounced() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        clickableText(R.string.folders_settings).performClick()
        awaitText(string(R.string.settings_title))
        assertClean("settings")
        assertAllControlsAreLabelled("settings")
        // Switches and radio rows expose their state and role, not only a label.
        val toggles = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        ).fetchSemanticsNodes()
        val selectable = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).fetchSemanticsNodes()
        Log.i(T17B_TAG, "settings: ${toggles.size} toggleable, ${selectable.size} selectable rows")
        assertTrue("no toggleable rows found in settings", toggles.isNotEmpty())
        assertTrue("no selectable rows found in settings", selectable.isNotEmpty())
        (toggles + selectable).forEach {
            assertTrue(
                "a state row has no role: ${it.boundsInRoot}",
                it.config.contains(SemanticsProperties.Role)
            )
        }
        // Section titles are headings, so a screen reader can jump between them.
        val headings = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).fetchSemanticsNodes()
        assertTrue("settings sections are not headings", headings.size >= MIN_SETTINGS_HEADINGS)
    }

    private companion object {
        const val MIN_SETTINGS_HEADINGS = 4
    }
}
