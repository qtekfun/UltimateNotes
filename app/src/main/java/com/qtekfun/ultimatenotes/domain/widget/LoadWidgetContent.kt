// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.domain.list.toListItem
import com.qtekfun.ultimatenotes.domain.lock.AppLockState
import com.qtekfun.ultimatenotes.domain.lock.LockConfigSource
import javax.inject.Inject

/**
 * Loads what the widget shows. Notes are read from the database only when the decision is to show
 * them, so a locked or signed-out widget never even touches note content.
 */
class LoadWidgetContent @Inject constructor(
    private val session: AccountSession,
    private val lock: AppLockState,
    private val lockConfig: LockConfigSource,
    private val noteDao: NoteDao,
    private val selector: WidgetContentSelector
) {
    suspend operator fun invoke(): WidgetContent {
        val signedIn = (session.activeAccount.value ?: session.restore()) != null
        val hidden = selector.isHidden(lockConfig.current().enabled, lock.locked.value)
        val notes = if (signedIn && !hidden) {
            noteDao.recent(WidgetContentSelector.MAX_NOTES).map { it.toListItem() }
        } else {
            emptyList()
        }
        return selector.select(signedIn, hidden, notes)
    }
}
