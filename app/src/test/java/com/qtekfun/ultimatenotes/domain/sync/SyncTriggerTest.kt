// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncTriggerTest {
    @Test
    fun `the default trigger returns at once without doing anything`() = runTest {
        assertEquals(Unit, NoopSyncTrigger.requestSync())
    }
}
