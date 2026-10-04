// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.folder

import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The folder drawer's contents, kept current as notes change. */
class ObserveFolders @Inject constructor(private val noteDao: NoteDao) {
    operator fun invoke(): Flow<FolderOverview> = combine(
        noteDao.observeFolderCounts(),
        noteDao.observeFavorites()
    ) { counts, favorites -> FolderOverview.from(counts, favorites.size) }
}
