// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.e2e

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.api.ApiResult
import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NoteWriteDto
import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.api.NotesListing
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.e2e.E2eEnvironment.PREFIX
import com.qtekfun.ultimatenotes.sync.SyncFixture
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * The real client ([NotesClient]) and the real sync engine over an in-memory Room, against a real
 * Nextcloud with the Notes app (see `e2e/setup.sh`). Each simulated client has its own database.
 * Every note and folder is named `[test]...` and the server is purged before and after each test.
 * Excluded from `check`: run it with `./gradlew e2eTest`.
 */
@Tag("e2e")
class NextcloudE2eTest {
    private lateinit var server: E2eServer
    private lateinit var api: NotesClient
    private val clients = mutableListOf<SyncFixture>()

    @BeforeEach
    fun setUp() {
        server = E2eEnvironment.server()
        api = server.newClient()
        runBlocking { E2eEnvironment.purge(api) }
    }

    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        if (::api.isInitialized) runBlocking { E2eEnvironment.purge(api) }
    }

    private fun client(chunkSize: Int = SyncFixture.CHUNK): SyncFixture =
        SyncFixture(server.newClient(), chunkSize = chunkSize).also { clients += it }

    private suspend fun onServer(): List<NoteDto> =
        E2eEnvironment.serverNotes(api).filter { it.title.startsWith(PREFIX) }

    private suspend fun SyncFixture.syncOk() {
        val result = sync()
        assertTrue(result is SyncResult.Success, "sync failed: $result")
    }

    private suspend fun SyncFixture.mine() =
        all().filter { it.title.startsWith(PREFIX) || it.content.startsWith(PREFIX) }

    private suspend fun create(note: NoteWriteDto): NoteDto {
        val result = api.createNote(note)
        assertTrue(result is ApiResult.Success, "create failed: $result")
        return (result as ApiResult.Success).value
    }

    @Test
    fun `create, update and delete reach the server and come back`() = runBlocking {
        val a = client()
        val localId = a.create("$PREFIX roundtrip\nfirst body", title = "$PREFIX roundtrip")
        a.syncOk()

        val created = onServer().single()
        assertEquals("$PREFIX roundtrip", created.title)
        assertEquals("$PREFIX roundtrip\nfirst body", created.content)
        val row = a.dao.get(localId)!!
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(created.id, row.id)
        assertEquals(created.etag, row.etag)

        a.edit(localId, "$PREFIX roundtrip\nsecond body")
        a.syncOk()
        assertEquals("$PREFIX roundtrip\nsecond body", onServer().single().content)
        assertEquals(SyncState.SYNCED, a.dao.get(localId)!!.syncState)

        a.delete(localId)
        a.syncOk()
        assertTrue(onServer().isEmpty())
        assertNull(a.dao.get(localId))
    }

    @Test
    fun `the sanitized title the server returns is adopted`() = runBlocking {
        val a = client()
        val localId = a.create("body", title = "$PREFIX a/b:c*d?e|f\"g<h>i\\j")
        a.syncOk()

        val serverTitle = onServer().single().title
        assertTrue(serverTitle.startsWith(PREFIX))
        assertFalse(
            serverTitle.any {
                it in "*|/\\:\"<>?"
            },
            "illegal characters kept: $serverTitle"
        )
        assertEquals(serverTitle, a.dao.get(localId)!!.title)
        assertEquals(SyncState.SYNCED, a.dao.get(localId)!!.syncState)
    }

    @Test
    fun `a title longer than 100 characters is trimmed and adopted`() = runBlocking {
        val a = client()
        val localId = a.create("body", title = PREFIX + " " + "x".repeat(150))
        a.syncOk()

        val serverTitle = onServer().single().title
        assertTrue(serverTitle.length <= MAX_TITLE, "title not trimmed: ${serverTitle.length}")
        assertEquals(serverTitle, a.dao.get(localId)!!.title)
    }

    @Test
    fun `duplicate titles get sequential numbers that the client adopts`() = runBlocking {
        val a = client()
        val first = a.create("one", title = "$PREFIX dup")
        val second = a.create("two", title = "$PREFIX dup")
        a.syncOk()

        assertEquals(
            setOf("$PREFIX dup", "$PREFIX dup (2)"),
            onServer().map { it.title }.toSet()
        )
        assertEquals("$PREFIX dup", a.dao.get(first)!!.title)
        assertEquals("$PREFIX dup (2)", a.dao.get(second)!!.title)

        // Resyncing must not rename anything or loop.
        a.syncOk()
        assertEquals(setOf("$PREFIX dup", "$PREFIX dup (2)"), onServer().map { it.title }.toSet())
    }

    @Test
    fun `renaming a note changes its title and not its content`() = runBlocking {
        val a = client()
        val localId = a.create("body", title = "$PREFIX before")
        a.syncOk()
        a.retitle(localId, "$PREFIX after")
        a.syncOk()

        val note = onServer().single()
        assertEquals("$PREFIX after", note.title)
        assertEquals("body", note.content)
    }

    @Test
    fun `moving to a folder and a subfolder reaches a second client`() = runBlocking {
        val a = client()
        val b = client()
        val localId = a.create("body", title = "$PREFIX move")
        a.syncOk()

        a.move(localId, "$PREFIX-folder")
        a.syncOk()
        assertEquals("$PREFIX-folder", onServer().single().category)
        b.syncOk()
        assertEquals("$PREFIX-folder", b.mine().single().category)

        a.move(localId, "$PREFIX-folder/Sub folder")
        a.syncOk()
        assertEquals("$PREFIX-folder/Sub folder", onServer().single().category)
        b.syncOk()
        assertEquals("$PREFIX-folder/Sub folder", b.mine().single().category)

        a.move(localId, "")
        a.syncOk()
        assertEquals("", onServer().single().category)
    }

    @Test
    fun `a note created straight in a subfolder keeps its folder`() = runBlocking {
        val a = client()
        a.create("body", category = "$PREFIX-top/Mid/Leaf", title = "$PREFIX deep")
        a.syncOk()
        assertEquals("$PREFIX-top/Mid/Leaf", onServer().single().category)
    }

    @Test
    fun `favorite is stored and returned`() = runBlocking {
        val a = client()
        val b = client()
        val localId = a.create("body", title = "$PREFIX fav")
        a.syncOk()
        a.favorite(localId, true)
        a.syncOk()
        assertTrue(onServer().single().favorite)
        b.syncOk()
        assertTrue(b.mine().single().favorite)

        a.favorite(localId, false)
        a.syncOk()
        assertFalse(onServer().single().favorite)
    }

    @Test
    fun `the list etag answers 304 until something changes`() = runBlocking {
        create(NoteWriteDto(title = "$PREFIX etag", content = "x"))
        val first = api.listNotes()
        val etag = ((first as ApiResult.Success).value as NotesListing.Page).page.etag
        assertNotNull(etag)

        val again = api.listNotes(ifNoneMatch = etag)
        assertEquals(NotesListing.NotModified, (again as ApiResult.Success).value)

        create(NoteWriteDto(title = "$PREFIX etag 2", content = "y"))
        val changed = api.listNotes(ifNoneMatch = etag)
        val page = (changed as ApiResult.Success).value
        assertTrue(page is NotesListing.Page, "the list changed but the server said 304")
    }

    @Test
    fun `a stale If-Match is a 412 with the server note, the current one is accepted`() =
        runBlocking {
            val note = create(NoteWriteDto(title = "$PREFIX match", content = "v1"))
            val okay = api.updateNote(note.id, note.etag, NoteWriteDto(content = "v2"))
            assertTrue(okay is ApiResult.Success, "current etag rejected: $okay")

            val stale = api.updateNote(note.id, note.etag, NoteWriteDto(content = "v3"))
            val failure = (stale as ApiResult.Failure).error
            assertTrue(failure is ApiError.Conflict, "expected 412, got $failure")
            assertEquals("v2", (failure as ApiError.Conflict).serverNote?.content)
        }

    @Test
    fun `chunked pagination walks every note and ends without a cursor`() = runBlocking {
        val ids = (1..NOTE_COUNT).map {
            create(NoteWriteDto(title = "$PREFIX page $it", content = "n$it")).id
        }.toSet()
        val seen = mutableSetOf<Long>()
        var cursor: String? = null
        var requests = 0
        do {
            val result = api.listNotes(chunkSize = SyncFixture.CHUNK, chunkCursor = cursor)
            val page = ((result as ApiResult.Success).value as NotesListing.Page).page
            seen += page.notes.map { it.id }
            cursor = page.nextCursor
            requests++
        } while (cursor != null && requests < MAX_REQUESTS)

        assertTrue(seen.containsAll(ids))
        assertTrue(requests > 1, "chunkSize was ignored: one request returned everything")
    }

    @Test
    fun `the engine pulls more notes than the chunk size and infers deletions`() = runBlocking {
        val ids = (1..NOTE_COUNT).map {
            create(NoteWriteDto(title = "$PREFIX bulk $it", content = "n$it")).id
        }
        val a = client(chunkSize = SyncFixture.CHUNK)
        a.syncOk()
        assertEquals(NOTE_COUNT, a.mine().size)

        api.deleteNote(ids.first())
        a.syncOk()
        assertEquals(NOTE_COUNT - 1, a.mine().size)
        assertTrue(a.mine().none { it.id == ids.first() })
    }

    @Test
    fun `a second sync with nothing new changes nothing`() = runBlocking {
        val a = client()
        a.create("body", title = "$PREFIX quiet")
        a.syncOk()
        val before = a.mine().map { it.copy(localId = 0) }
        a.syncOk()
        a.syncOk()
        assertEquals(before, a.mine().map { it.copy(localId = 0) })
    }

    @Test
    fun `an edit on the server and one offline leave a conflict copy and lose no text`() =
        runBlocking {
            val a = client()
            val b = client()
            val localId = a.create("base", title = "$PREFIX conflict")
            a.syncOk()
            b.syncOk()

            // B edits and syncs; A edits offline with the old etag, then comes online.
            b.edit(b.mine().single().localId, "from b")
            b.syncOk()
            a.edit(localId, "from a")
            a.syncOk()

            val notes = onServer()
            assertEquals(2, notes.size, "expected the original and one conflict copy")
            val original = notes.single { !it.title.contains("conflicto") }
            val copy = notes.single { it.title.contains("conflicto") }
            assertEquals("from b", original.content)
            assertEquals("from a", copy.content)
            assertEquals("$PREFIX conflict", original.title)
            assertTrue(copy.title.startsWith("$PREFIX conflict (conflicto "))
            assertTrue(a.mine().all { it.syncState == SyncState.SYNCED })
        }

    @Test
    fun `a delete on the server and an edit offline recreate the note`() = runBlocking {
        val a = client()
        val localId = a.create("base", title = "$PREFIX revive")
        a.syncOk()
        api.deleteNote(onServer().single().id)

        a.edit(localId, "edited offline")
        a.syncOk()
        assertEquals("edited offline", onServer().single().content)
    }

    @Test
    fun `two clients that worked offline converge with all their notes`() = runBlocking {
        val a = client()
        val b = client()
        val shared = a.create("shared", title = "$PREFIX shared")
        a.syncOk()
        b.syncOk()

        // Both go offline and work.
        a.create("only a", category = "$PREFIX-a", title = "$PREFIX only a")
        a.edit(shared, "shared, edited by a")
        b.favorite(b.create("only b", title = "$PREFIX only b"), true)

        // They come back in turn, twice, to settle.
        repeat(2) {
            a.syncOk()
            b.syncOk()
        }
        a.syncOk()

        val remote = onServer()
        assertEquals(
            setOf("only a", "only b", "shared, edited by a"),
            remote.map { it.content }.toSet()
        )
        fun List<NoteEntity>.view() = map { listOf(it.title, it.content, it.category, it.favorite) }
        assertEquals(a.mine().view().toSet(), b.mine().view().toSet())
        val onRemote = remote.map { listOf(it.title, it.content, it.category, it.favorite) }
        assertEquals(onRemote.toSet(), a.mine().view().toSet())
        assertTrue(remote.single { it.content == "only b" }.favorite)
        assertTrue((a.mine() + b.mine()).all { it.syncState == SyncState.SYNCED })
    }

    /**
     * Known limitation (docs/decisions/0004-e2e.md): the engine keeps no base text, so a favorite
     * or folder change racing a remote edit of the same note counts as a text conflict. Nothing
     * is lost, but a copy of the unchanged text appears.
     */
    @Test
    fun `a favorite racing a remote edit loses nothing and both clients converge`() = runBlocking {
        val a = client()
        val b = client()
        val shared = a.create("shared", title = "$PREFIX race")
        a.syncOk()
        b.syncOk()

        a.edit(shared, "shared, edited by a")
        b.favorite(b.mine().single().localId, true)
        repeat(2) {
            a.syncOk()
            b.syncOk()
        }
        a.syncOk()

        val remote = onServer()
        assertTrue(remote.any { it.content == "shared, edited by a" }, "the remote edit was lost")
        assertTrue(remote.size <= 2, "more copies than the one conflict: ${remote.size}")
        fun List<NoteEntity>.view() = map { listOf(it.title, it.content, it.category, it.favorite) }
        assertEquals(a.mine().view().toSet(), b.mine().view().toSet())
        assertEquals(remote.size, a.mine().size)
        assertTrue((a.mine() + b.mine()).all { it.syncState == SyncState.SYNCED })
    }

    @Test
    fun `unicode, emoji and CRLF survive a round trip`() = runBlocking {
        val a = client()
        val b = client()
        val text = "$PREFIX unicode\r\n" +
            "Ünïcödé · 日本語 · العربية · 🎉👩‍👩‍👧 · ñandú\r\n" +
            "\r\n- [ ] todo\r\n- [x] done\r\n\ttabbed\r\n  trailing  "
        a.create(text, title = "$PREFIX unicode")
        a.syncOk()

        assertEquals(text, onServer().single().content)
        b.syncOk()
        assertEquals(text, b.mine().single().content)
    }

    @Test
    fun `markdown the editor does not know is stored byte for byte`() = runBlocking {
        val a = client()
        val text = "| a | b |\n|---|---|\n| 1 | 2 |\n\n<div class=\"x\">html</div>\n\n" +
            "```kotlin\nval x = 1\n```\n"
        a.create(text, title = "$PREFIX markdown")
        a.syncOk()
        assertEquals(text, onServer().single().content)
    }

    @Test
    fun `a wrong password is Unauthorized and stops the sync`() = runBlocking {
        val bad = E2eServer(server.url, server.user, "not-the-password")
        val fixture = SyncFixture(bad.newClient()).also { clients += it }
        val result = fixture.sync()
        assertTrue(result is SyncResult.Failed)
        assertEquals(ApiError.Unauthorized, (result as SyncResult.Failed).error)
    }

    private companion object {
        const val MAX_TITLE = 100
        const val NOTE_COUNT = 5
        const val MAX_REQUESTS = 20
    }
}
