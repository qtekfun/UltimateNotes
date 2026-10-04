// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Search becomes accent insensitive: the full-text index is recreated with the `unicode61`
 * tokenizer and filled again from the notes, which are not touched.
 */
internal object Migration1To2 : Migration(1, 2) {
    private const val TRIGGER_PREFIX = "room_fts_content_sync_note_fts_"

    override suspend fun migrate(connection: SQLiteConnection) {
        listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT").forEach {
            connection.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_PREFIX$it")
        }
        connection.execSQL("DROP TABLE IF EXISTS `note_fts`")
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `note_fts` USING FTS4(`title` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, content=`note`, tokenize=unicode61 `remove_diacritics=1`)"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${TRIGGER_PREFIX}BEFORE_UPDATE BEFORE UPDATE ON `note` " +
                "BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${TRIGGER_PREFIX}BEFORE_DELETE BEFORE DELETE ON `note` " +
                "BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${TRIGGER_PREFIX}AFTER_UPDATE AFTER UPDATE ON `note` " +
                "BEGIN INSERT INTO `note_fts`(`docid`, `title`, `content`) " +
                "VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${TRIGGER_PREFIX}AFTER_INSERT AFTER INSERT ON `note` " +
                "BEGIN INSERT INTO `note_fts`(`docid`, `title`, `content`) " +
                "VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END"
        )
        connection.execSQL("INSERT INTO `note_fts`(`note_fts`) VALUES('rebuild')")
    }
}
