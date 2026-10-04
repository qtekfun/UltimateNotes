// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.auth.AuthModule
import com.qtekfun.ultimatenotes.di.DatabaseModule
import com.qtekfun.ultimatenotes.di.SettingsModule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Test

/**
 * T17b: Compose's own accessibility checks (Accessibility Test Framework on the semantics tree).
 * With them enabled, every action the test performs (click, type) first checks the screen and
 * throws on an accessibility ERROR (touch target, missing label, contrast...). So driving the main
 * flows through the UI audits each screen they pass through. Not a TalkBack run.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class, AuthModule::class, SettingsModule::class)
class ComposeAccessibilityChecksTest : DeviceTestBase() {
    @Before
    fun seedAndEnableChecks() {
        seedDemoNotes()
        compose.enableAccessibilityChecks()
    }

    @Test
    fun theMainFlowsHaveNoAccessibilityErrors() {
        launch()
        awaitText(string(R.string.section_pinned))
        openDrawer()
        clickableText(R.string.folders_settings).performClick()
        awaitText(string(R.string.settings_title))
        byDescription(R.string.settings_back).performClick()
        awaitText(string(R.string.section_pinned))
        compose.onNodeWithText("Lisbon trip plan").performClick()
        awaitDescription(string(R.string.editor_text_field))
        byDescription(R.string.format_bold).performClick()
        byDescription(R.string.editor_more).performClick()
        // Back closes the menu, the keyboard and then the editor, in that order.
        repeat(MAX_BACKS) {
            if (!hasNodesWithText(string(R.string.section_pinned))) pressBack()
        }
        awaitText(string(R.string.section_pinned))
        byDescription(R.string.search_field).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lemon")
        awaitText("Lemon cake")
        byDescription(R.string.search_close).performClick()
    }

    private companion object {
        const val MAX_BACKS = 4
    }
}
