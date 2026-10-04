// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.mapper

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NoteWriteDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteMappersTest {
    @Test
    fun `a server note becomes a synced row with every field`() {
        val dto = NoteDto(
            id = 7,
            etag = "abc",
            readonly = true,
            modified = 1_700_000_000,
            title = "Title",
            category = "Work/Meetings",
            content = "# Title\nbody",
            favorite = true
        )

        assertEquals(
            NoteEntity(
                localId = 3,
                id = 7,
                etag = "abc",
                readonly = true,
                modified = 1_700_000_000,
                title = "Title",
                category = "Work/Meetings",
                content = "# Title\nbody",
                favorite = true,
                syncState = SyncState.SYNCED,
                lastSyncedEtag = "abc"
            ),
            dto.toSyncedEntity(localId = 3)
        )
    }

    @Test
    fun `missing optional fields fall back to empty values`() {
        val entity = NoteDto(id = 1).toSyncedEntity()

        assertEquals(0, entity.localId)
        assertEquals("", entity.etag)
        assertEquals("", NoteDto(id = 1).localEtag)
        assertEquals("e", NoteDto(id = 1, etag = "e").localEtag)
        assertEquals("", entity.content)
        assertNull(entity.lastSyncedEtag)
    }

    @Test
    fun `a row is uploaded with its content, folder and favorite only`() {
        val entity =
            NoteEntity(
                id = 1,
                title = "derived",
                category = "Home",
                content = "text",
                favorite = true
            )

        assertEquals(
            NoteWriteDto(content = "text", category = "Home", favorite = true),
            entity.toWriteDto()
        )
    }
}
