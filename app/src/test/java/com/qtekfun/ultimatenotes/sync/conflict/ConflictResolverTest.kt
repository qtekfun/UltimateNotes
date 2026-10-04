// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.conflict

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.NoteBase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.TEST_CLOCK
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictResolverTest {
    private val resolver = ConflictResolver(TEST_CLOCK)

    private fun local(
        content: String,
        category: String = "Work",
        favorite: Boolean = false,
        title: String = "Plan",
        base: NoteBase? = null
    ) = NoteEntity(
        localId = 5,
        id = 9,
        etag = "old",
        modified = 100,
        title = title,
        category = category,
        content = content,
        favorite = favorite,
        syncState = SyncState.DIRTY,
        lastSyncedEtag = "old",
        base = base
    )

    private fun server(
        content: String,
        category: String = "Work",
        title: String = "Plan",
        favorite: Boolean = false
    ) = NoteDto(
        id = 9,
        etag = "new",
        readonly = true,
        modified = 200,
        title = title,
        category = category,
        content = content,
        favorite = favorite
    )

    private fun serverRow(
        content: String,
        category: String = "Work",
        title: String = "Plan",
        favorite: Boolean = false
    ) = NoteEntity(
        localId = 5,
        id = 9,
        etag = "new",
        readonly = true,
        modified = 200,
        title = title,
        category = category,
        content = content,
        favorite = favorite,
        syncState = SyncState.SYNCED,
        lastSyncedEtag = "new",
        base = NoteBase.of(content, title, category, favorite)
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
                content = "Plan\nmine",
                syncState = SyncState.NEW
            ),
            fork.copy
        )
    }

    @Test
    fun `the copy keeps the local title and its body is not altered`() {
        val fork = resolver.resolve(
            local("## Plan\r\n- a\r\n", title = "My plan"),
            server("x", title = "Their plan")
        ) as Resolution.Fork

        assertEquals("My plan (conflicto 2026-10-04)", fork.copy.title)
        assertEquals("## Plan\r\n- a\r\n", fork.copy.content)
        assertEquals("Their plan", fork.original.title)
    }

    @Test
    fun `a local note without a title is labelled with its first line`() {
        val fork = resolver.resolve(
            local("\n\n## Hello\nbody", title = ""),
            server("x", title = "Other")
        ) as Resolution.Fork

        assertEquals("Hello (conflicto 2026-10-04)", fork.copy.title)
    }

    @Test
    fun `a local note with neither title nor words still gets a dated title`() {
        val fork = resolver.resolve(local("-\n#", title = ""), server("x")) as Resolution.Fork

        assertEquals("(conflicto 2026-10-04)", fork.copy.title)
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

        assertEquals("Plan (conflicto 2027-01-01)", fork.copy.title)
    }

    @Test
    fun `a differing title with the same text is a real change and forks, losing neither title`() {
        val fork = resolver.resolve(
            local("Same", title = "Mine"),
            server("Same", title = "Theirs")
        ) as Resolution.Fork

        assertEquals("Theirs", fork.original.title)
        assertEquals("Same", fork.original.content)
        assertEquals("Mine (conflicto 2026-10-04)", fork.copy.title)
        assertEquals("Same", fork.copy.content)
        assertEquals(SyncState.NEW, fork.copy.syncState)
    }

    @Test
    fun `a blank local title is no edit, so the server title is adopted with the etag`() {
        val result = resolver.resolve(local("Same", title = ""), server("Same", title = "Theirs"))
        val note = (result as Resolution.Replace).note

        assertEquals("new", note.etag)
        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals("Theirs", note.title)
    }

    @Test
    fun `identical text and title adopts the server etag and is synced`() {
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

    @Test
    fun `a blank local note whose title was edited is kept as a copy`() {
        val fork = resolver.resolve(
            local("  \n", title = "Mine"),
            server("Their text", title = "Theirs")
        ) as Resolution.Fork

        assertEquals(serverRow("Their text", title = "Theirs"), fork.original)
        assertEquals("Mine (conflicto 2026-10-04)", fork.copy.title)
    }

    // Three-way merge (ADR 0011): the base is what the note was at the last sync.

    private val ancestor = NoteBase.of("base text", "Plan", "Work", false)

    private fun merged(local: NoteEntity, server: NoteDto): NoteEntity =
        (resolver.resolve(local, server) as Resolution.Replace).note

    @Test
    fun `a favorite set here and a text edited there merge without a conflict copy`() {
        val note = merged(
            local("base text", favorite = true, base = ancestor),
            server("their text")
        )

        assertEquals("their text", note.content)
        assertTrue(note.favorite)
        assertEquals(SyncState.DIRTY, note.syncState)
        assertEquals("new", note.lastSyncedEtag)
        assertEquals(NoteBase.of("their text", "Plan", "Work", false), note.base)
        assertEquals(200, note.modified)
    }

    @Test
    fun `a text edited here and a folder or favorite changed there merge keeping both`() {
        val note = merged(
            local("my text", base = ancestor),
            server("base text", category = "Home", favorite = true)
        )

        assertEquals("my text", note.content)
        assertEquals("Home", note.category)
        assertTrue(note.favorite)
        assertEquals(SyncState.DIRTY, note.syncState)
        assertEquals(200, note.modified)
    }

    @Test
    fun `a title edited on one side and the text on the other merge`() {
        val titled = merged(
            local("base text", title = "Mine", base = ancestor),
            server("their text")
        )
        val retitledThere = merged(
            local("my text", base = ancestor),
            server("base text", title = "Theirs")
        )

        assertEquals("Mine" to "their text", titled.title to titled.content)
        assertEquals("Theirs" to "my text", retitledThere.title to retitledThere.content)
    }

    @Test
    fun `the same change on both sides merges silently and the note is synced`() {
        val note = merged(
            local("same", category = "Home", favorite = true, title = "T", base = ancestor),
            server("same", category = "Home", favorite = true, title = "T")
        )

        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals(200, note.modified)
        assertEquals(serverRow("same", "Home", "T", true), note)
    }

    @Test
    fun `nothing changed here means the server version is simply taken`() {
        val note = merged(
            local("base text", base = ancestor),
            server("their text", category = "Home", title = "Theirs", favorite = true)
        )

        assertEquals(serverRow("their text", "Home", "Theirs", true), note)
    }

    @Test
    fun `a blank local title is no edit and the server title is taken`() {
        val note =
            merged(
                local("my text", title = "", base = ancestor),
                server("base text", title = "Theirs")
            )

        assertEquals("Theirs", note.title)
        assertEquals("my text", note.content)
    }

    @Test
    fun `a folder or favorite changed to different values on both sides is won by the local one`() {
        val note = merged(
            local("base text", category = "Home", favorite = true, base = ancestor),
            server("base text", category = "Other", favorite = true)
        )
        val unstarredHere = merged(
            local("base text", base = ancestor.copy(favorite = true)),
            server("base text", favorite = true)
        )

        assertEquals("Home", note.category)
        assertTrue(note.favorite)
        assertEquals(SyncState.DIRTY, note.syncState)
        assertFalse(unstarredHere.favorite)
        assertEquals(SyncState.DIRTY, unstarredHere.syncState)
    }

    @Test
    fun `text changed to different values on both sides forks and keeps the local choices`() {
        val fork = resolver.resolve(
            local("my text", favorite = true, category = "Home", base = ancestor),
            server("their text", category = "Other")
        ) as Resolution.Fork

        assertEquals("their text", fork.original.content)
        assertEquals("Home", fork.original.category)
        assertTrue(fork.original.favorite)
        assertEquals(SyncState.DIRTY, fork.original.syncState)
        assertEquals(200, fork.original.modified)
        assertEquals(NoteBase.of("their text", "Plan", "Other", false), fork.original.base)
        assertEquals("Plan (conflicto 2026-10-04)", fork.copy.title)
        assertEquals("my text", fork.copy.content)
        assertEquals(null, fork.copy.base)
    }

    @Test
    fun `a fork with no local folder or favorite choice leaves the server row untouched`() {
        val fork = resolver.resolve(
            local("my text", base = ancestor),
            server("their text")
        ) as Resolution.Fork

        assertEquals(serverRow("their text"), fork.original)
    }

    @Test
    fun `a title changed to different values on both sides forks even if the text merges`() {
        val fork = resolver.resolve(
            local("base text", title = "Mine", base = ancestor),
            server("their text", title = "Theirs")
        ) as Resolution.Fork

        assertEquals("Theirs" to "their text", fork.original.title to fork.original.content)
        assertEquals("Mine (conflicto 2026-10-04)", fork.copy.title)
        assertEquals("base text", fork.copy.content)
    }

    @Test
    fun `a blank local text is replaced by the server text but a clashing title still forks`() {
        val replaced = merged(local("  ", favorite = true, base = ancestor), server("their text"))
        val fork = resolver.resolve(
            local("  ", title = "Mine", base = ancestor),
            server("their text", title = "Theirs")
        )

        assertEquals("their text", replaced.content)
        assertTrue(replaced.favorite)
        assertTrue(fork is Resolution.Fork)
    }

    @Test
    fun `without a base the conservative rules still apply`() {
        val fork = resolver.resolve(
            local("my text", favorite = true),
            server("their text")
        )
        val same = merged(local("same", favorite = true), server("same"))

        assertTrue(fork is Resolution.Fork)
        assertEquals(SyncState.DIRTY, same.syncState)
        assertEquals(NoteBase.of("same", "Plan", "Work", false), same.base)
    }
}
