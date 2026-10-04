// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FtsQueryTest {
    @Test
    fun `each word becomes a quoted prefix term`() {
        assertEquals("\"milk*\" \"eggs*\"", FtsQuery.fromUserInput("  milk \t eggs\n"))
    }

    @Test
    fun `double quotes are dropped so they cannot break the expression`() {
        assertEquals("\"hi*\" \"there*\"", FtsQuery.fromUserInput("\"hi\"there"))
        assertNull(FtsQuery.fromUserInput("\""))
    }

    @Test
    fun `blank input has no query`() {
        assertNull(FtsQuery.fromUserInput(" \n "))
        assertNull(FtsQuery.fromUserInput(""))
    }
}
