// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppVersionTest {
    @Test
    fun parsesFinalRelease() {
        val version = AppVersion.parse("1.2.3")
        assertEquals(AppVersion(1, 2, 3, null), version)
        assertFalse(version!!.isPreRelease)
        assertEquals("1.2.3", version.toString())
    }

    @Test
    fun parsesReleaseCandidate() {
        val version = AppVersion.parse("0.1.0-rc.4")
        assertEquals(AppVersion(0, 1, 0, 4), version)
        assertTrue(version!!.isPreRelease)
        assertEquals("0.1.0-rc.4", version.toString())
    }

    @Test
    fun rejectsMalformedVersions() {
        listOf("", "1.2", "1.2.3-beta", "v1.2.3", "1.2.3-rc.", "1.2.3-rc.x").forEach {
            assertNull(AppVersion.parse(it), it)
        }
    }
}
