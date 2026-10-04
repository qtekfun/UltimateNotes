// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Runs on a device (`connectedDebugAndroidTest`); `check` only compiles it. The exported schemas in
 * app/schemas are packaged as test assets. Every new database version must add a test that creates
 * the previous version, inserts a note, migrates and checks the note survived.
 */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("migration-test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = UltimateNotesDatabase::class
    )

    @Test
    fun latestSchemaIsCreatedAndValidatedAgainstTheEntities() = runBlocking {
        helper.createDatabase(UltimateNotesDatabase.VERSION).close()

        helper.runMigrationsAndValidate(
            UltimateNotesDatabase.VERSION,
            UltimateNotesDatabase.MIGRATIONS.toList()
        ).use { connection ->
            val count = connection.prepare("SELECT COUNT(*) FROM note").use {
                it.step()
                it.getLong(0)
            }
            assertEquals(0L, count)
        }
    }

    @Test
    fun version1To2KeepsTheNotesAndMakesSearchIgnoreAccents() = runBlocking {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO note(etag, readonly, modified, title, category, content, favorite, " +
                    "syncState) VALUES ('', 0, 0, 'Café', '', 'solo', 0, 'SYNCED')"
            )
        }

        helper.runMigrationsAndValidate(2, UltimateNotesDatabase.MIGRATIONS.toList()).use {
            val notes = it.prepare("SELECT COUNT(*) FROM note").use { statement ->
                statement.step()
                statement.getLong(0)
            }
            val found = it.prepare("SELECT COUNT(*) FROM note_fts WHERE note_fts MATCH 'cafe'")
                .use { statement ->
                    statement.step()
                    statement.getLong(0)
                }
            assertEquals(1L, notes)
            assertEquals(1L, found)
        }
    }
}
