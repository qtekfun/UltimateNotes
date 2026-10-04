// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.store

import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.sync.work.SyncErrorKind
import com.qtekfun.ultimatenotes.sync.work.SyncPhase
import com.qtekfun.ultimatenotes.sync.work.SyncStatus
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedPreferencesSyncStatusStoreTest {
    private val prefs = FakePreferences()
    private val at = Instant.parse("2026-10-04T10:00:00Z")

    private fun restarted() = SharedPreferencesSyncStatusStore(prefs)

    @Test
    fun `a fresh install is idle and never synced`() {
        assertEquals(SyncStatus(), restarted().status.value)
    }

    @Test
    fun `syncing is shown but not persisted`() {
        val store = restarted()

        store.markSyncing()

        assertEquals(SyncPhase.Syncing, store.status.value.phase)
        assertEquals(SyncStatus(), restarted().status.value)
    }

    @Test
    fun `an error survives a restart and a success clears it`() {
        restarted().markError(SyncErrorKind.UNAUTHORIZED)

        val store = restarted()
        assertEquals(SyncPhase.Error(SyncErrorKind.UNAUTHORIZED), store.status.value.phase)

        store.markSynced(at)
        assertEquals(SyncStatus(SyncPhase.Idle, at), store.status.value)
        assertEquals(SyncStatus(SyncPhase.Idle, at), restarted().status.value)
    }

    @Test
    fun `an error keeps the last synced time`() {
        val store = restarted()
        store.markSynced(at)

        store.markError(SyncErrorKind.OFFLINE)

        assertEquals(
            SyncStatus(SyncPhase.Error(SyncErrorKind.OFFLINE), at),
            restarted().status.value
        )
    }

    @Test
    fun `an unknown stored error is ignored`() {
        prefs.values["error"] = "GREMLINS"

        assertEquals(SyncPhase.Idle, restarted().status.value.phase)
    }
}
