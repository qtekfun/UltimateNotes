// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delayed deletion with undo (SPEC §7). Scheduled notes are only hidden ([hidden]); the DELETED
 * state is committed through [commit] once [window] has passed without [undo]. Scheduling another
 * deletion commits the previous one at once, since the user can only undo the latest. The delay
 * runs on [scope]'s dispatcher, so tests drive it with virtual time.
 */
class UndoableDeletes(
    private val scope: CoroutineScope,
    private val window: Duration,
    private val commit: suspend (List<Long>) -> Unit
) {
    private val mutableHidden = MutableStateFlow<Set<Long>>(emptySet())
    private val lock = Mutex()
    private var timer: Job? = null

    /** Ids scheduled for deletion, to leave out of the list while the undo window is open. */
    val hidden: StateFlow<Set<Long>> = mutableHidden.asStateFlow()

    /** Hides [ids] and starts the undo window; whatever was pending is committed first. */
    suspend fun schedule(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        lock.withLock {
            finish(commitNow = true)
            mutableHidden.value = ids.toSet()
            timer = scope.launch {
                delay(window)
                lock.withLock { finish(commitNow = true, cancelTimer = false) }
            }
        }
    }

    /** Cancels the pending deletion and shows its notes again. False if nothing was pending. */
    suspend fun undo(): Boolean = lock.withLock {
        if (mutableHidden.value.isEmpty()) return@withLock false
        finish(commitNow = false)
        true
    }

    /** Commits the pending deletion without waiting out the window (e.g. when leaving the app). */
    suspend fun flush() = lock.withLock { finish(commitNow = true) }

    /** Ends the pending deletion. The timer must not cancel itself, or the commit would be too. */
    private suspend fun finish(commitNow: Boolean, cancelTimer: Boolean = true) {
        if (cancelTimer) timer?.cancel()
        timer = null
        val ids = mutableHidden.value
        if (ids.isEmpty()) return
        try {
            if (commitNow) commit(ids.toList())
        } finally {
            mutableHidden.value = emptySet()
        }
    }
}
