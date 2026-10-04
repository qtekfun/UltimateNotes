// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.list.toListItem
import com.qtekfun.ultimatenotes.domain.lock.AppLockState
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Emits when the widget may be out of date: its notes changed (a local edit or a sync, both end up
 * in the database), the lock engaged or was switched, or the account changed. Bursts are merged
 * into one emission and notes that do not change what the widget shows are ignored, so there is no
 * polling and a sync of thousands of notes costs a single refresh.
 */
class WidgetChanges @Inject constructor(
    private val noteDao: NoteDao,
    private val lock: AppLockState,
    private val settings: SettingsRepository,
    private val session: AccountSession,
    private val selector: WidgetContentSelector
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observe(quietPeriod: Duration = QUIET_PERIOD): Flow<Unit> = merge(
        noteDao.observeRecent(WidgetContentSelector.MAX_NOTES)
            .map { notes -> selector.toWidgetNotes(notes.map { it.toListItem() }) }
            .distinctUntilChanged()
            .map { },
        lock.locked.map { },
        settings.settings.map { it.appLockEnabled }.distinctUntilChanged().map { },
        session.activeAccount.map { }
    ).debounce(quietPeriod)

    private companion object {
        val QUIET_PERIOD = 2.seconds
    }
}
