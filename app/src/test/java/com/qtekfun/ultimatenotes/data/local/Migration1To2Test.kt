// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Runs the migration on a real SQLite, starting from the version 1 tables. */
class Migration1To2Test {
    private lateinit var connection: SQLiteConnection

    @BeforeEach
    fun createVersion1() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(
            "CREATE TABLE `note` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `content` TEXT NOT NULL)"
        )
        connection.execSQL(
            "CREATE VIRTUAL TABLE `note_fts` USING FTS4(`title` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, content=`note`)"
        )
        listOf("BEFORE_UPDATE" to "BEFORE UPDATE", "BEFORE_DELETE" to "BEFORE DELETE").forEach {
            connection.execSQL(
                "CREATE TRIGGER fts_sync_${it.first} ${it.second} ON `note` " +
                    "BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END"
            )
        }
        listOf("AFTER_UPDATE" to "AFTER UPDATE", "AFTER_INSERT" to "AFTER INSERT").forEach {
            connection.execSQL(
                "CREATE TRIGGER fts_sync_${it.first} ${it.second} ON `note` " +
                    "BEGIN INSERT INTO `note_fts`(`docid`, `title`, `content`) " +
                    "VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END"
            )
        }
    }

    @AfterEach
    fun close() = connection.close()

    private fun count(sql: String, argument: String? = null): Long = connection.prepare(sql).use {
        if (argument != null) it.bindText(1, argument)
        it.step()
        it.getLong(0)
    }

    private fun matches(match: String) =
        count("SELECT COUNT(*) FROM note_fts WHERE note_fts MATCH ?", match)

    @Test
    fun `existing notes become searchable without accents and the index keeps following edits`() =
        runTest {
            connection.execSQL("INSERT INTO note(title, content) VALUES ('Café', 'solo')")
            assertEquals(0, matches("cafe"), "version 1 does not fold accents")

            Migration1To2.migrate(connection)

            assertEquals(1, matches("cafe"))
            assertEquals(1, matches("café"))
            connection.execSQL("INSERT INTO note(title, content) VALUES ('AÑO', 'nuevo')")
            assertEquals(1, matches("ano"))
            connection.execSQL("UPDATE note SET title = 'Té' WHERE title = 'Café'")
            assertEquals(0, matches("cafe"))
            assertEquals(1, matches("te"))
            connection.execSQL("DELETE FROM note WHERE title = 'Té'")
            assertEquals(0, matches("te"))
            assertEquals(1, count("SELECT COUNT(*) FROM note"), "the notes are untouched")
        }

    @Test
    fun `it is the step from version 1 to 2`() {
        assertEquals(1 to 2, Migration1To2.startVersion to Migration1To2.endVersion)
    }
}
