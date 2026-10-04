// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatenotes.sync.work.SyncErrorKind
import com.qtekfun.ultimatenotes.sync.work.SyncPhase
import com.qtekfun.ultimatenotes.sync.work.SyncStatus
import com.qtekfun.ultimatenotes.sync.work.SyncStatusStore
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps the last error and the last successful sync in a private [SharedPreferences] file, so they
 * survive the process; "syncing" is transient and never stored (a killed run is not running).
 */
class SharedPreferencesSyncStatusStore(private val prefs: SharedPreferences) : SyncStatusStore {
    private val state = MutableStateFlow(load())
    override val status: StateFlow<SyncStatus> = state.asStateFlow()

    override fun markSyncing() = state.update { it.copy(phase = SyncPhase.Syncing) }

    override fun markSynced(at: Instant) {
        prefs.edit {
            remove(KEY_ERROR)
            putLong(KEY_LAST_SYNCED, at.toEpochMilli())
        }
        state.value = SyncStatus(SyncPhase.Idle, at)
    }

    override fun markError(kind: SyncErrorKind) {
        prefs.edit { putString(KEY_ERROR, kind.name) }
        state.update { it.copy(phase = SyncPhase.Error(kind)) }
    }

    private fun load(): SyncStatus {
        val kind = SyncErrorKind.entries.firstOrNull { it.name == prefs.getString(KEY_ERROR, null) }
        return SyncStatus(
            phase = kind?.let { SyncPhase.Error(it) } ?: SyncPhase.Idle,
            lastSyncedAt = if (prefs.contains(KEY_LAST_SYNCED)) {
                Instant.ofEpochMilli(prefs.getLong(KEY_LAST_SYNCED, 0))
            } else {
                null
            }
        )
    }

    private companion object {
        const val KEY_ERROR = "error"
        const val KEY_LAST_SYNCED = "last_synced_at"
    }
}
