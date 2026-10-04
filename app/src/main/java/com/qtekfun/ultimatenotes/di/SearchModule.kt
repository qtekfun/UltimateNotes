// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import com.qtekfun.ultimatenotes.domain.search.NoteSearcher
import com.qtekfun.ultimatenotes.domain.search.RoomNoteSearcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/** Bindings of the search (T12). */
@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    const val SEARCH_DEBOUNCE = "searchDebounce"
    private const val DEBOUNCE_MILLIS = 200L

    @Provides
    fun noteSearcher(searcher: RoomNoteSearcher): NoteSearcher = searcher

    /** Milliseconds the user must stop typing before a search runs. */
    @Provides
    @Named(SEARCH_DEBOUNCE)
    fun searchDebounce(): Long = DEBOUNCE_MILLIS
}
