// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.qtekfun.ultimatenotes.data.local.model.NoteBase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Runs the migration on a real SQLite, starting from the version 2 `note` table. */
class Migration2To3Test {
    private lateinit var connection: SQLiteConnection

    @BeforeEach
    fun createVersion2() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(
            "CREATE TABLE `note` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`id` INTEGER, `etag` TEXT NOT NULL, `readonly` INTEGER NOT NULL, " +
                "`modified` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, `favorite` INTEGER NOT NULL, " +
                "`syncState` TEXT NOT NULL, `lastSyncedEtag` TEXT)"
        )
    }

    @AfterEach
    fun close() = connection.close()

    private fun insert(
        state: String,
        title: String,
        content: String,
        category: String = "",
        favorite: Int = 0
    ) = connection.execSQL(
        "INSERT INTO note(id, etag, readonly, modified, title, category, content, favorite, " +
            "syncState, lastSyncedEtag) VALUES (1, 'e', 0, 5, '$title', '$category', '$content', " +
            "$favorite, '$state', 'e')"
    )

    private data class Row(
        val hash: String?,
        val title: String?,
        val category: String?,
        val favorite: Long?,
        val content: String
    )

    private fun rows(): List<Row> = connection.prepare(
        "SELECT baseContentHash, baseTitle, baseCategory, baseFavorite, content FROM note " +
            "ORDER BY localId"
    ).use {
        buildList {
            while (it.step()) {
                add(
                    Row(
                        hash = if (it.isNull(0)) null else it.getText(0),
                        title = if (it.isNull(1)) null else it.getText(1),
                        category = if (it.isNull(2)) null else it.getText(2),
                        favorite = if (it.isNull(3)) null else it.getLong(3),
                        content = it.getText(4)
                    )
                )
            }
        }
    }

    @Test
    fun `synced notes get their current values as the merge base`() = runTest {
        insert("SYNCED", "Plan", "line 1\nline 2 ñ", category = "Work/Reports", favorite = 1)

        Migration2To3.migrate(connection)

        assertEquals(
            listOf(
                Row(
                    NoteBase.hash("line 1\nline 2 ñ"),
                    "Plan",
                    "Work/Reports",
                    1L,
                    "line 1\nline 2 ñ"
                )
            ),
            rows()
        )
    }

    @Test
    fun `notes that are not synced get no base so they are resolved conservatively`() = runTest {
        insert("DIRTY", "a", "edited")
        insert("NEW", "b", "new")
        insert("DELETED", "c", "gone")
        insert("SYNCED", "d", "same")

        Migration2To3.migrate(connection)

        val migrated = rows()
        assertEquals(listOf("edited", "new", "gone", "same"), migrated.map { it.content })
        assertEquals(listOf(null, null, null), migrated.take(3).map { it.hash })
        assertNull(migrated[0].favorite)
        assertEquals(NoteBase.hash("same"), migrated[3].hash)
        assertEquals(0L, migrated[3].favorite)
    }

    @Test
    fun `an empty database migrates`() = runTest {
        Migration2To3.migrate(connection)

        assertEquals(emptyList<Row>(), rows())
    }

    @Test
    fun `it is the step from version 2 to 3`() {
        assertEquals(2 to 3, Migration2To3.startVersion to Migration2To3.endVersion)
    }
}
