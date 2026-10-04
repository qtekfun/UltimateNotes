// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.qtekfun.ultimatenotes.data.local.model.NoteBase

/**
 * Three-way merge (ADR 0011): each note gets the columns of its merge base. A note that is in sync
 * (SYNCED) is, by definition, equal to the server's copy, so its base is seeded with its current
 * values. Any other note (DIRTY, NEW, DELETED) keeps a null base and is resolved conservatively,
 * exactly as before, until its next successful sync. The notes themselves are not touched.
 */
internal object Migration2To3 : Migration(startVersion = 2, endVersion = 3) {
    // The same positions serve the SELECT (0 is the id) and the 1-based UPDATE parameters.
    private const val CONTENT = 1
    private const val TITLE = 2
    private const val CATEGORY = 3
    private const val FAVORITE = 4
    private const val ID_PARAMETER = 5

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `note` ADD COLUMN `baseContentHash` TEXT")
        connection.execSQL("ALTER TABLE `note` ADD COLUMN `baseTitle` TEXT")
        connection.execSQL("ALTER TABLE `note` ADD COLUMN `baseCategory` TEXT")
        connection.execSQL("ALTER TABLE `note` ADD COLUMN `baseFavorite` INTEGER")
        val seeds = connection.prepare(
            "SELECT `localId`, `content`, `title`, `category`, `favorite` FROM `note` " +
                "WHERE `syncState` = 'SYNCED'"
        ).use { select ->
            buildList {
                while (select.step()) {
                    add(
                        select.getLong(0) to NoteBase.of(
                            content = select.getText(CONTENT),
                            title = select.getText(TITLE),
                            category = select.getText(CATEGORY),
                            favorite = select.getLong(FAVORITE) != 0L
                        )
                    )
                }
            }
        }
        connection.prepare(
            "UPDATE `note` SET `baseContentHash` = ?, `baseTitle` = ?, `baseCategory` = ?, " +
                "`baseFavorite` = ? WHERE `localId` = ?"
        ).use { update ->
            seeds.forEach { (localId, base) ->
                update.bindText(CONTENT, base.contentHash)
                update.bindText(TITLE, base.title)
                update.bindText(CATEGORY, base.category)
                update.bindLong(FAVORITE, if (base.favorite) 1 else 0)
                update.bindLong(ID_PARAMETER, localId)
                update.step()
                update.reset()
            }
        }
    }
}
