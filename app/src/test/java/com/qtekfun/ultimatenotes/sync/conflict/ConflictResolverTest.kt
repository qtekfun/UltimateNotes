// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.conflict

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.TEST_CLOCK
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictResolverTest {
    private val resolver = ConflictResolver(TEST_CLOCK)

    private fun local(content: String, category: String = "Work", favorite: Boolean = false) =
        NoteEntity(
            localId = 5,
            id = 9,
            etag = "old",
            modified = 100,
            title = "t",
            category = category,
            content = content,
            favorite = favorite,
            syncState = SyncState.DIRTY,
            lastSyncedEtag = "old"
        )

    private fun server(content: String, category: String = "Work") = NoteDto(
        id = 9,
        etag = "new",
        readonly = true,
        modified = 200,
        title = "server title",
        category = category,
        content = content
    )

    private fun serverRow(content: String, category: String = "Work") = NoteEntity(
        localId = 5,
        id = 9,
        etag = "new",
        readonly = true,
        modified = 200,
        title = "server title",
        category = category,
        content = content,
        syncState = SyncState.SYNCED,
        lastSyncedEtag = "new"
    )

    @Test
    fun `different text keeps the server version and saves the local text as a new note`() {
        val result = resolver.resolve(local("Plan\nmine"), server("Plan\ntheirs"))

        val fork = result as Resolution.Fork
        assertEquals(serverRow("Plan\ntheirs"), fork.original)
        assertEquals(
            NoteEntity(
                modified = 100,
                title = "Plan (conflicto 2026-10-04)",
                category = "Work",
                content = "Plan (conflicto 2026-10-04)\nmine",
                syncState = SyncState.NEW
            ),
            fork.copy
        )
    }

    @Test
    fun `the copy lands in the local folder even if the server moved the note`() {
        val fork = resolver.resolve(
            local("A\nx", category = "Home"),
            server("A\ny", category = "Work")
        )
            as Resolution.Fork

        assertEquals("Home", fork.copy.category)
        assertEquals("Work", fork.original.category)
    }

    @Test
    fun `the date comes from the injected clock in its own zone`() {
        val late = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.ofHours(2))

        val fork = ConflictResolver(late).resolve(local("A\nx"), server("A\ny")) as Resolution.Fork

        assertEquals("A (conflicto 2027-01-01)\nx", fork.copy.content)
    }

    @Test
    fun `heading markers stay out of the title but the text is kept whole`() {
        val fork = resolver.resolve(local("## Plan\r\n- a\r\n"), server("x")) as Resolution.Fork

        assertEquals("## Plan (conflicto 2026-10-04)\r\n- a\r\n", fork.copy.content)
        assertEquals("Plan (conflicto 2026-10-04)", fork.copy.title)
    }

    @Test
    fun `leading blank lines are kept and the first line with text gets the suffix`() {
        val fork = resolver.resolve(local("\n\n  Hello\nbody"), server("x")) as Resolution.Fork

        assertEquals("\n\n  Hello (conflicto 2026-10-04)\nbody", fork.copy.content)
    }

    @Test
    fun `identical text adopts the server etag and is synced`() {
        val result = resolver.resolve(local("Same"), server("Same"))

        val note = (result as Resolution.Replace).note
        assertEquals("new", note.etag)
        assertEquals("new", note.lastSyncedEtag)
        assertEquals(SyncState.SYNCED, note.syncState)
        assertTrue(note.readonly)
        assertEquals(100, note.modified)
        assertEquals(5, note.localId)
    }

    @Test
    fun `identical text with a different folder or favorite keeps the local values to upload`() {
        val moved = resolver.resolve(
            local("Same", category = "Home"),
            server("Same")
        ) as Resolution.Replace
        val starred = resolver.resolve(
            local("Same", favorite = true),
            server("Same")
        ) as Resolution.Replace

        assertEquals(SyncState.DIRTY, moved.note.syncState)
        assertEquals("Home", moved.note.category)
        assertEquals("new", moved.note.lastSyncedEtag)
        assertEquals(SyncState.DIRTY, starred.note.syncState)
        assertTrue(starred.note.favorite)
    }

    @Test
    fun `a blank local note has no text to keep so the server version replaces it`() {
        val result = resolver.resolve(local("  \n"), server("Their text"))

        assertEquals(Resolution.Replace(serverRow("Their text")), result)
    }
}
