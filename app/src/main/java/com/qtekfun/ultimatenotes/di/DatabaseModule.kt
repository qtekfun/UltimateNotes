// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSearchDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the database; repositories take the DAOs they need. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "ultimatenotes.db"

    // The spread copies a tiny array once, when the database is created.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): UltimateNotesDatabase =
        Room.databaseBuilder<UltimateNotesDatabase>(context, DATABASE_NAME)
            // The system SQLite keeps the APK small; tests use the bundled build with the same API.
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*UltimateNotesDatabase.MIGRATIONS)
            .build()

    @Provides
    fun noteDao(database: UltimateNotesDatabase): NoteDao = database.noteDao()

    @Provides
    fun noteSearchDao(database: UltimateNotesDatabase): NoteSearchDao = database.noteSearchDao()

    @Provides
    fun noteSyncDao(database: UltimateNotesDatabase): NoteSyncDao = database.noteSyncDao()
}
