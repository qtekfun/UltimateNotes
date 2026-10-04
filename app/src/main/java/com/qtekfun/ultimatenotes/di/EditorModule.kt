// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import android.content.Context
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.editor.DefaultNoteTitle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Bindings of the editor. */
@Module
@InstallIn(SingletonComponent::class)
object EditorModule {
    @Provides
    fun defaultNoteTitle(@ApplicationContext context: Context): DefaultNoteTitle =
        DefaultNoteTitle { context.getString(R.string.note_new_title) }
}
