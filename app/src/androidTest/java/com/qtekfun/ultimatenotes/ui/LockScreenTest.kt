// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.ui.lock.LockScreen
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled by `check`, run on a device: the lock screen and its unlock button. */
class LockScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun unlockButtonAsksForAuthentication() {
        var taps = 0
        compose.setContent { UltimateNotesTheme { LockScreen(onUnlock = { taps++ }) } }

        compose.onNodeWithText(text(R.string.lock_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.lock_unlock)).performClick()

        assertEquals(1, taps)
    }
}
