// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.FakeNotesServer
import com.qtekfun.ultimatenotes.sync.FakeNotesServer.Fault
import com.qtekfun.ultimatenotes.sync.InMemoryCheckpointStore
import com.qtekfun.ultimatenotes.sync.SyncFixture
import com.qtekfun.ultimatenotes.sync.TEST_CLOCK
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The push half of [SyncEngine] (the queue), against the faithful fake server. */
class SyncEnginePushTest {
    private val server = FakeNotesServer()
    private val client = SyncFixture(server)

    @AfterEach
    fun close() = client.close()

    private fun success(result: SyncResult): SyncReport {
        assertTrue(result is SyncResult.Success, "expected success but was $result")
        return result.report
    }

    private fun failure(result: SyncResult): ApiError = (result as SyncResult.Failed).error

    @Test
    fun `a new note is created on the server and becomes synced`() = runBlocking {
        val localId = client.create("Fresh\nbody", category = "Work")

        val report = success(client.sync())

        assertEquals(1, report.pushed)
        val note = checkNotNull(client.dao.get(localId))
        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals(server.ids().single(), note.id)
        assertEquals(server.etagOf(note.id!!), note.etag)
        assertEquals(note.etag, note.lastSyncedEtag)
        assertEquals(listOf(Triple("Fresh\nbody", "Work", false)), server.snapshot())
    }

    @Test
    fun `an edited note is sent with If-Match and becomes synced`() = runBlocking {
        server.put("A")
        client.sync()
        val id = client.byContent("A").localId
        client.edit(id, "A edited")
        client.move(id, "Home")
        client.favorite(id, true)

        val report = success(client.sync())

        assertEquals(1, report.pushed)
        assertEquals(listOf(Triple("A edited", "Home", true)), server.snapshot())
        assertEquals(SyncState.SYNCED, checkNotNull(client.dao.get(id)).syncState)
        assertTrue(server.requests.any { it.startsWith("PUT") })
    }

    @Test
    fun `a new note is posted with its title and adopts the one the server stored`() = runBlocking {
        server.put("x", title = "Dup")
        val numbered = client.create("body", title = "Dup")
        val sanitized = client.create("body2", title = "What: now?")

        val report = success(client.sync())

        assertEquals(2, report.pushed)
        assertEquals("Dup (2)", checkNotNull(client.dao.get(numbered)).title)
        assertEquals("What now", checkNotNull(client.dao.get(sanitized)).title)
        assertEquals(listOf("Dup", "Dup (2)", "What now"), server.titles())
        assertEquals(setOf(SyncState.SYNCED), client.all().map { it.syncState }.toSet())
    }

    @Test
    fun `renaming a note uploads the title alone and the content does not rename it`() =
        runBlocking {
            val a = server.put("Body line\nrest", title = "Old title")
            client.sync()
            val id = client.byContent("Body line\nrest").localId

            client.retitle(id, "New title")
            success(client.sync())
            assertEquals("New title", server.titleOf(a))
            assertEquals(listOf("Body line\nrest"), server.contents())

            client.edit(id, "Another first line\nrest")
            success(client.sync())
            assertEquals("New title", server.titleOf(a))
            assertEquals("New title", checkNotNull(client.dao.get(id)).title)
        }

    @Test
    fun `a title typed while the note is being uploaded is kept and uploaded next`() = runBlocking {
        val id = client.create("body", title = "First")
        server.onRequest = { if (it == "POST") client.retitle(id, "Typed meanwhile") }

        success(client.sync())

        val during = checkNotNull(client.dao.get(id))
        assertEquals("Typed meanwhile", during.title)
        assertEquals(SyncState.DIRTY, during.syncState)

        success(client.sync())
        assertEquals(listOf("Typed meanwhile"), server.titles())
        assertEquals(SyncState.SYNCED, checkNotNull(client.dao.get(id)).syncState)
    }

    @Test
    fun `a deleted note is deleted on the server and its tombstone removed`() = runBlocking {
        server.put("A")
        client.sync()
        client.delete(client.byContent("A").localId)

        val report = success(client.sync())

        assertEquals(1, report.deleted)
        assertEquals(0, server.size)
        assertTrue(client.all().isEmpty())
    }

    @Test
    fun `deleting a note the server already lost counts as done`() = runBlocking {
        server.put("A")
        client.sync()
        client.delete(client.byContent("A").localId)
        // Gone between the pull and the DELETE.
        server.onRequest = { if (it.startsWith("DELETE")) server.ids().forEach(server::remove) }

        val report = success(client.sync())

        assertEquals(1, report.deleted)
        assertTrue(client.all().isEmpty())
    }

    @Test
    fun `a tombstone without a server id never reaches the network`() = runBlocking {
        client.dao.insert(NoteEntity(content = "never uploaded", syncState = SyncState.DELETED))

        success(client.sync())

        assertTrue(client.all().isEmpty())
        assertEquals(listOf("GET"), server.requests)
    }

    @Test
    fun `a dirty note without a server id is uploaded as new`() = runBlocking {
        client.dao.insert(NoteEntity(content = "orphan", syncState = SyncState.DIRTY))

        success(client.sync())

        assertEquals(listOf("orphan"), server.contents())
        assertEquals(SyncState.SYNCED, client.all().single().syncState)
    }

    @Test
    fun `a dirty note without a known server version is never written blindly`() = runBlocking {
        server.put("A")
        client.sync()
        val row = client.byContent("A")
        client.dao.update(
            row.copy(content = "edited", syncState = SyncState.DIRTY, lastSyncedEtag = null)
        )

        val report = success(client.sync())

        assertTrue(server.requests.none { it.startsWith("PUT") })
        assertTrue(report.skipped >= 1)
        assertEquals(listOf("A"), server.contents())
    }

    @Test
    fun `a rejected edit keeps the server text and stores the local text as a copy`() =
        runBlocking {
            val a = server.put("Title\nserver body", category = "Work")
            client.sync()
            val id = client.byContent("Title\nserver body").localId
            client.edit(id, "Title\nmy body")
            // Another client edits after our pull, so the PUT meets a different etag.
            server.onRequest = { if (it.startsWith("PUT")) server.edit(a, "Title\nother body") }

            val report = success(client.sync())

            assertEquals(1, report.forked)
            assertEquals(1, report.pushed)
            val original = checkNotNull(client.dao.get(id))
            assertEquals("Title\nother body", original.content)
            assertEquals(SyncState.SYNCED, original.syncState)
            val copy = client.all().single { it.localId != id }
            assertEquals("Title\nmy body", copy.content)
            assertEquals("Title (conflicto 2026-10-04)", copy.title)
            assertEquals("Work", copy.category)
            assertEquals(SyncState.SYNCED, copy.syncState)
            assertEquals(
                setOf("Title\nother body", "Title\nmy body"),
                server.contents().toSet()
            )
            assertEquals(setOf("Title", "Title (conflicto 2026-10-04)"), server.titles().toSet())
        }

    @Test
    fun `a rejected favorite meets a remote text edit and merges instead of forking`() =
        runBlocking {
            val a = server.put("Title\nserver body", category = "Work")
            client.sync()
            val id = client.byContent("Title\nserver body").localId
            client.favorite(id, true)
            server.onRequest = { if (it.startsWith("PUT")) server.edit(a, "Title\nother body") }

            val report = success(client.sync())

            assertEquals(0, report.forked)
            assertEquals(0, report.pushed)
            assertEquals(1, report.pulled)
            val merged = checkNotNull(client.dao.get(id))
            assertEquals("Title\nother body", merged.content)
            assertTrue(merged.favorite)
            assertEquals(SyncState.DIRTY, merged.syncState)
            server.onRequest = {}

            assertEquals(1, success(client.sync()).pushed)

            assertEquals(1, client.all().size)
            assertEquals(listOf(Triple("Title\nother body", "Work", true)), server.snapshot())
            assertEquals(SyncState.SYNCED, checkNotNull(client.dao.get(id)).syncState)
        }

    @Test
    fun `a rejected edit with the same text adopts the etag and uploads the rest next`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            val id = client.byContent("A").localId
            client.edit(id, "A2")
            client.move(id, "Home")
            server.onRequest = { if (it.startsWith("PUT")) server.edit(a, "A2") }

            val report = success(client.sync())

            assertEquals(1, report.pulled)
            val adopted = checkNotNull(client.dao.get(id))
            assertEquals(SyncState.DIRTY, adopted.syncState)
            assertEquals(server.etagOf(a), adopted.lastSyncedEtag)
            assertEquals(listOf(Triple("A2", "", false)), server.snapshot())

            server.onRequest = {}
            success(client.sync())
            assertEquals(listOf(Triple("A2", "Home", false)), server.snapshot())
            assertEquals(SyncState.SYNCED, checkNotNull(client.dao.get(id)).syncState)
        }

    @Test
    fun `a rejected edit whose server note cannot be read is left for the next run`() =
        runBlocking {
            server.put("A")
            client.sync()
            client.edit(client.byContent("A").localId, "A edited")
            server.fault =
                { if (it.startsWith("PUT")) Fault.Status(HTTP_PRECONDITION_FAILED) else null }

            val report = success(client.sync())

            assertEquals(1, report.skipped)
            assertEquals(SyncState.DIRTY, client.all().single().syncState)
        }

    @Test
    fun `an edit to a note the server deleted meanwhile is recreated as a new note`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            val id = client.byContent("A").localId
            client.edit(id, "A edited")
            server.onRequest = { if (it.startsWith("PUT")) server.remove(a) }

            val report = success(client.sync())

            assertEquals(1, report.pushed)
            assertEquals(listOf("A edited"), server.contents())
            val note = checkNotNull(client.dao.get(id))
            assertEquals(SyncState.SYNCED, note.syncState)
            assertTrue(note.id != a)
        }

    @Test
    fun `an edit made while the upload is in flight is kept and uploaded on the next run`() =
        runBlocking {
            server.put("A")
            client.sync()
            val id = client.byContent("A").localId
            client.edit(id, "first edit")
            server.onRequest = { if (it.startsWith("PUT")) client.edit(id, "second edit") }

            success(client.sync())

            val during = checkNotNull(client.dao.get(id))
            assertEquals("second edit", during.content)
            assertEquals(SyncState.DIRTY, during.syncState)
            assertEquals(server.etagOf(during.id!!), during.lastSyncedEtag)
            assertEquals(listOf("first edit"), server.contents())

            server.onRequest = {}
            success(client.sync())
            assertEquals(listOf("second edit"), server.contents())
            assertEquals(SyncState.SYNCED, checkNotNull(client.dao.get(id)).syncState)
        }

    @Test
    fun `a note created offline and edited during the upload stays dirty on top of the new id`() =
        runBlocking {
            val id = client.create("draft")
            server.onRequest = { if (it == "POST") client.edit(id, "draft v2") }

            success(client.sync())

            val during = checkNotNull(client.dao.get(id))
            assertEquals(SyncState.DIRTY, during.syncState)
            assertEquals("draft v2", during.content)
            server.onRequest = {}
            success(client.sync())
            assertEquals(listOf("draft v2"), server.contents())
        }

    @Test
    fun `a delete issued during the upload wins over the pushed edit`() = runBlocking {
        server.put("A")
        client.sync()
        val id = client.byContent("A").localId
        client.edit(id, "A edited")
        server.onRequest = { if (it.startsWith("PUT")) client.delete(id) }

        success(client.sync())
        assertEquals(SyncState.DELETED, checkNotNull(client.dao.get(id)).syncState)
        server.onRequest = {}
        success(client.sync())

        assertEquals(0, server.size)
        assertTrue(client.all().isEmpty())
    }

    @Test
    fun `an edit during a rejected upload is not overwritten by the conflict resolution`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            val id = client.byContent("A").localId
            client.edit(id, "A edited")
            server.onRequest = {
                if (it.startsWith("PUT")) {
                    server.edit(a, "A from the server")
                    client.edit(id, "A edited again")
                }
            }

            val report = success(client.sync())

            assertEquals(1, report.skipped)
            assertEquals("A edited again", checkNotNull(client.dao.get(id)).content)
        }

    @Test
    fun `a rejected edit with the same text is skipped if the user changed the note meanwhile`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            val id = client.byContent("A").localId
            client.edit(id, "A2")
            client.move(id, "Home")
            server.onRequest = {
                if (it.startsWith("PUT")) {
                    server.edit(a, "A2")
                    client.favorite(id, true)
                }
            }

            val report = success(client.sync())

            assertEquals(1, report.skipped)
            val note = checkNotNull(client.dao.get(id))
            assertEquals("Home", note.category)
            assertTrue(note.favorite)
        }

    @Test
    fun `a recreate that loses the race to the user is skipped`() = runBlocking {
        val a = server.put("A")
        client.sync()
        val id = client.byContent("A").localId
        client.edit(id, "A edited")
        server.onRequest = {
            if (it.startsWith("PUT")) {
                server.remove(a)
                client.edit(id, "A edited again")
            }
        }

        val report = success(client.sync())

        assertEquals(1, report.skipped)
        assertEquals("A edited again", checkNotNull(client.dao.get(id)).content)
    }

    @Test
    fun `offline stops the queue and keeps everything pending`() = runBlocking {
        client.create("One")
        client.create("Two")
        server.fault = { if (it == "POST") Fault.Offline else null }

        val result = client.sync()

        assertEquals(ApiError.Offline, failure(result))
        assertEquals(listOf("GET", "POST"), server.requests)
        assertEquals(setOf(SyncState.NEW), client.all().map { it.syncState }.toSet())
    }

    @Test
    fun `systemic errors on upload stop the run`() = runBlocking {
        client.create("One")
        client.create("Two")
        val errors = mapOf(
            HTTP_UNAUTHORIZED to ApiError.Unauthorized,
            HTTP_NOT_FOUND to ApiError.NotesAppMissing,
            HTTP_BAD_REQUEST to ApiError.UnsupportedApi
        )
        for ((code, error) in errors) {
            server.requests.clear()
            server.fault = { if (it == "POST") Fault.Status(code) else null }
            assertEquals(error, failure(client.sync()))
            assertEquals(listOf("GET", "POST"), server.requests)
        }
    }

    @Test
    fun `an error specific to one note does not block the rest of the queue`() = runBlocking {
        client.create("Bad")
        client.create("Good")
        var posts = 0
        server.fault =
            { if (it == "POST" && posts++ == 0) Fault.Status(HTTP_SERVER_ERROR) else null }

        val report = success(client.sync())

        assertEquals(1, report.skipped)
        assertEquals(1, report.pushed)
        assertEquals(listOf("Good"), server.contents())
        assertEquals(SyncState.NEW, client.byContent("Bad").syncState)
    }

    @Test
    fun `a read-only rejection of an edit or a delete is skipped, not fatal`() = runBlocking {
        server.put("A")
        server.put("B")
        client.sync()
        client.edit(client.byContent("A").localId, "A edited")
        client.delete(client.byContent("B").localId)
        server.fault =
            {
                if (it.startsWith("PUT") ||
                    it.startsWith("DELETE")
                ) {
                    Fault.Status(HTTP_FORBIDDEN)
                } else {
                    null
                }
            }

        val report = success(client.sync())

        assertEquals(2, report.skipped)
        assertEquals(
            setOf(SyncState.DIRTY, SyncState.DELETED),
            client.all().map {
                it.syncState
            }.toSet()
        )
    }

    @Test
    fun `syncing again after a success changes nothing`() = runBlocking {
        server.put("Remote")
        client.create("Local")
        client.sync()
        val rows = client.all()
        val snapshot = server.snapshot()

        val report = success(client.sync())

        assertEquals(SyncReport(), report)
        assertEquals(rows, client.all())
        assertEquals(snapshot, server.snapshot())
    }

    @Test
    fun `a run that failed halfway is finished by the next one without duplicates`() = runBlocking {
        client.create("One")
        client.create("Two")
        client.create("Three")
        server.fault = { if (it == "POST" && server.size == 1) Fault.Offline else null }
        client.sync()
        server.fault = { null }

        success(client.sync())

        assertEquals(listOf("One", "Three", "Two"), server.contents().sorted())
        assertEquals(setOf(SyncState.SYNCED), client.all().map { it.syncState }.toSet())
    }

    @Test
    fun `the default chunk size is large enough for one request`() = runBlocking {
        repeat(5) { server.put("Note $it") }
        val engine = SyncEngine(
            client = NotesClient(server, Dispatchers.Unconfined),
            reads = client.database.noteSyncDao(),
            writes = client.dao,
            checkpoints = InMemoryCheckpointStore(),
            resolver = ConflictResolver(TEST_CLOCK),
            io = Dispatchers.Unconfined
        )

        success(engine.sync())

        assertEquals(listOf(SyncEngine.DEFAULT_CHUNK_SIZE), server.listCalls.map { it.chunkSize })
        assertEquals(5, client.all().size)
    }

    @Test
    fun `concurrent runs are serialized`() = runBlocking {
        server.put("A")
        val gate = CompletableDeferred<Unit>()
        server.onRequest = { if (server.requests.size == 1) gate.await() }

        val first = async(Dispatchers.Default) { client.sync() }
        val second = async(Dispatchers.Default) { client.sync() }
        while (server.requests.isEmpty()) Thread.sleep(POLL_MS)
        Thread.sleep(POLL_MS * SETTLE)
        assertEquals(listOf("GET"), server.requests)
        gate.complete(Unit)

        success(first.await())
        success(second.await())
        assertEquals(listOf("GET", "GET"), server.requests)
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PRECONDITION_FAILED = 412
        const val HTTP_SERVER_ERROR = 500
        const val POLL_MS = 10L
        const val SETTLE = 10
    }
}
