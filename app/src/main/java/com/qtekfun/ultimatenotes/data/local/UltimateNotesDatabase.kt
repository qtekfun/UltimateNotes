// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSearchDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.entity.NoteFtsEntity

/** Local source of truth. Schemas are exported to app/schemas and versioned. */
@Database(
    entities = [NoteEntity::class, NoteFtsEntity::class],
    version = UltimateNotesDatabase.VERSION,
    exportSchema = true
)
abstract class UltimateNotesDatabase : RoomDatabase() {
    companion object {
        const val VERSION = 1

        /**
         * Migrations from each released version to the next. There is no destructive fallback:
         * raising [VERSION] requires adding its migration here (checked by DatabaseSchemaTest).
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }

    abstract fun noteDao(): NoteDao

    abstract fun noteSearchDao(): NoteSearchDao

    abstract fun noteSyncDao(): NoteSyncDao

    abstract fun noteSyncWriteDao(): NoteSyncWriteDao
}
