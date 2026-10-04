// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.di.ListModule
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Every listable note as a row, kept current as notes change. Filtering is left to the caller.
 * Building rows reads each note's text, so it runs on [dispatcher], not on the collector's thread.
 */
class ObserveNotes @Inject constructor(
    private val noteDao: NoteDao,
    @Named(ListModule.LIST_DISPATCHER) private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<List<NoteListItem>> =
        noteDao.observeAll().map { notes -> notes.map { it.toListItem() } }.flowOn(dispatcher)
}
