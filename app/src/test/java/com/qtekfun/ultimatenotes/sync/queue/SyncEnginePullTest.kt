// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.FakeNotesServer
import com.qtekfun.ultimatenotes.sync.FakeNotesServer.Fault
import com.qtekfun.ultimatenotes.sync.SyncFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pull half of [SyncEngine], against the faithful fake server. */
class SyncEnginePullTest {
    private val server = FakeNotesServer()
    private var racy: (suspend (NoteEntity) -> Unit)? = null
    private var racyDelete: (suspend (NoteEntity) -> Unit)? = null
    private val client = SyncFixture(server) { real ->
        RacyDao(real, { racy?.invoke(it) }, { racyDelete?.invoke(it) })
    }

    @AfterEach
    fun close() = client.close()

    /** A DAO that lets a test edit a note right after the engine read it. */
    private class RacyDao(
        private val real: NoteSyncWriteDao,
        private val afterRead: suspend (NoteEntity) -> Unit,
        private val beforeDelete: suspend (NoteEntity) -> Unit
    ) : NoteSyncWriteDao by real {
        override suspend fun getByRemoteId(id: Long): NoteEntity? =
            real.getByRemoteId(id)?.also { afterRead(it) }

        override suspend fun deleteIfUnchanged(expected: NoteEntity): Boolean {
            beforeDelete(expected)
            return real.deleteIfUnchanged(expected)
        }
    }

    private fun success(result: SyncResult): SyncReport {
        assertTrue(result is SyncResult.Success, "expected success but was $result")
        return result.report
    }

    @Test
    fun `first sync downloads every note as synced and stores the checkpoint`() = runBlocking {
        server.put("One\nbody", category = "Work", favorite = true)
        server.put("Two")

        val report = success(client.sync())

        assertEquals(2, report.pulled)
        val one = client.byContent("One\nbody")
        assertEquals(SyncState.SYNCED, one.syncState)
        assertEquals("Work", one.category)
        assertTrue(one.favorite)
        assertEquals(one.etag, one.lastSyncedEtag)
        assertEquals(server.etagOf(one.id!!), one.etag)
        val checkpoint = client.checkpoints.current
        assertNotNull(checkpoint.listEtag)
        assertNotNull(checkpoint.pruneBefore)
    }

    @Test
    fun `an unchanged server answers 304 and nothing is touched`() = runBlocking {
        server.put("One")
        client.sync()
        val before = client.all()

        val report = success(client.sync())

        assertEquals(SyncReport(), report)
        assertEquals(before, client.all())
        assertEquals(client.checkpoints.current.listEtag, server.listCalls.last().ifNoneMatch)
    }

    @Test
    fun `after a change only that note is downloaded, using pruneBefore`() = runBlocking {
        server.put("One")
        val two = server.put("Two")
        client.sync()
        val checkpoint = client.checkpoints.current
        server.edit(two, "Two edited")

        val report = success(client.sync())

        assertEquals(1, report.pulled)
        assertEquals(checkpoint.pruneBefore, server.listCalls.last().pruneBefore)
        assertEquals(listOf("One", "Two edited"), client.visible().map { it.first })
        assertEquals(0, report.removed)
    }

    @Test
    fun `chunks are followed by cursor and the list etag is sent on the first request only`() =
        runBlocking {
            repeat(5) { server.put("Note $it") }

            success(client.sync())

            val calls = server.listCalls
            assertEquals(3, calls.size)
            assertEquals(listOf(null, "2", "4"), calls.map { it.chunkCursor })
            assertEquals(setOf(SyncFixture.CHUNK), calls.map { it.chunkSize }.toSet())
            assertEquals(5, client.all().size)
            client.sync()
            assertEquals(1, server.listCalls.takeLast(1).count { it.ifNoneMatch != null })
        }

    @Test
    fun `a walk cut short infers no deletions and saves no checkpoint`() = runBlocking {
        val ids = listOf("A", "B", "C", "D").map { server.put(it) }
        client.sync()
        val saved = client.checkpoints.current
        server.remove(ids[0])
        ids.drop(1).forEach { server.edit(it, "edited $it") }
        // Three changed notes make two chunks; the second request of the walk fails.
        val firstCall = server.listCalls.size
        server.fault = { if (server.listCalls.size == firstCall + 2) Fault.Offline else null }

        val result = client.sync()

        assertEquals(ApiError.Offline, (result as SyncResult.Failed).error)
        assertEquals(4, client.all().size)
        assertTrue(client.all().any { it.content == "A" })
        assertEquals(saved, client.checkpoints.current)
    }

    @Test
    fun `a complete walk removes synced notes the server no longer has`() = runBlocking {
        val a = server.put("A")
        server.put("B")
        client.sync()
        server.remove(a)

        val report = success(client.sync())

        assertEquals(1, report.removed)
        assertEquals(listOf("B"), client.all().map { it.content })
    }

    @Test
    fun `a removal is skipped if the user edited the note meanwhile, and the edit is recreated`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            server.remove(a)
            racyDelete = { row ->
                racyDelete = null
                client.dao.update(
                    row.copy(content = "typed meanwhile", syncState = SyncState.DIRTY)
                )
            }

            val report = success(client.sync())

            assertEquals(1, report.skipped)
            assertEquals(0, report.removed)
            assertEquals("typed meanwhile", client.all().single().content)
            // No checkpoint was saved, so the next walk is complete again and recreates the note.
            success(client.sync())
            assertEquals(listOf("typed meanwhile"), server.contents())
        }

    @Test
    fun `a locally deleted note that is already gone on the server is dropped without a request`() =
        runBlocking {
            val a = server.put("A")
            server.put("B")
            client.sync()
            client.delete(client.byContent("A").localId)
            server.remove(a)

            val report = success(client.sync())

            assertEquals(1, report.removed)
            assertTrue(server.requests.none { it.startsWith("DELETE") })
            assertEquals(listOf("B"), client.all().map { it.content })
        }

    @Test
    fun `a note edited locally that the server deleted is recreated, never lost`() = runBlocking {
        val a = server.put("A")
        server.put("B")
        client.sync()
        client.edit(client.byContent("A").localId, "A edited offline")
        server.remove(a)

        val report = success(client.sync())

        assertEquals(1, report.pushed)
        assertEquals(listOf("A edited offline", "B"), server.contents().sorted())
        val recreated = client.byContent("A edited offline")
        assertEquals(SyncState.SYNCED, recreated.syncState)
        assertTrue(recreated.id != a)
    }

    @Test
    fun `a stale checkpoint with a bare id for an unknown note triggers one full re-list`() =
        runBlocking {
            server.put("A")
            server.put("B")
            server.put("C")
            client.sync()
            // Local data lost (e.g. restored backup) while the checkpoint survived.
            client.all().forEach { client.dao.delete(it.localId) }
            server.edit(server.ids().first(), "A2")

            val report = success(client.sync())

            assertEquals(3, report.pulled)
            assertEquals(listOf("A2", "B", "C"), client.visible().map { it.first })
            val last = server.listCalls.last()
            assertNull(last.pruneBefore)
            assertNull(last.ifNoneMatch)
        }

    @Test
    fun `a server that keeps sending bare ids is an invalid response, not a loop`() = runBlocking {
        server.put("A")
        server.alwaysPrune = true

        val result = client.sync()

        assertEquals(ApiError.InvalidResponse, (result as SyncResult.Failed).error)
        assertTrue(client.all().isEmpty())
    }

    @Test
    fun `server changes replace a synced note`() = runBlocking {
        val a = server.put("A")
        client.sync()
        val localId = client.byContent("A").localId
        server.edit(a, "A from the server")

        success(client.sync())

        val note = checkNotNull(client.dao.get(localId))
        assertEquals("A from the server", note.content)
        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals(server.etagOf(a), note.etag)
    }

    @Test
    fun `an edit made on the server beats a local delete and the note comes back`() = runBlocking {
        val a = server.put("A")
        client.sync()
        client.delete(client.byContent("A").localId)
        server.edit(a, "A edited elsewhere")

        success(client.sync())

        val note = client.all().single()
        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals("A edited elsewhere", note.content)
        assertEquals(listOf("A edited elsewhere"), server.contents())
        assertTrue(server.requests.none { it.startsWith("DELETE") })
    }

    @Test
    fun `a local delete of a note the server did not change still goes through`() = runBlocking {
        server.put("A")
        client.sync()
        client.delete(client.byContent("A").localId)
        client.checkpoints.current = SyncCheckpoint() // the note is listed in full, unchanged

        val report = success(client.sync())

        assertEquals(1, report.deleted)
        assertEquals(0, server.size)
    }

    @Test
    fun `a pulled note keeps the server title as it is, not the first line`() = runBlocking {
        val a = server.put("First line\nbody", title = "Custom title")

        success(client.sync())

        assertEquals("Custom title", client.byContent("First line\nbody").title)

        server.retitle(a, "Renamed on the web")
        success(client.sync())

        val row = client.byContent("First line\nbody")
        assertEquals("Renamed on the web", row.title)
        assertEquals(SyncState.SYNCED, row.syncState)
    }

    @Test
    fun `an unsynced title edit is never overwritten by a pull`() = runBlocking {
        val a = server.put("A")
        client.sync()
        val id = client.byContent("A").localId
        server.retitle(a, "Server title")
        racy = { row ->
            racy = null
            client.dao.update(row.copy(title = "typed title", syncState = SyncState.DIRTY))
        }

        val report = success(client.sync())

        assertTrue(report.skipped >= 1)
        // The push then meets the server change (412): neither title is lost.
        assertEquals(1, report.forked)
        assertEquals("Server title", checkNotNull(client.dao.get(id)).title)
        assertEquals(
            setOf("Server title", "typed title (conflicto 2026-10-04)"),
            server.titles().toSet()
        )
    }

    @Test
    fun `different titles with the same text are a conflict that keeps both`() = runBlocking {
        val a = server.put("A")
        client.sync()
        client.retitle(client.byContent("A").localId, "Mine")
        server.retitle(a, "Theirs")

        val report = success(client.sync())

        assertEquals(1, report.forked)
        assertEquals(setOf("Theirs", "Mine (conflicto 2026-10-04)"), server.titles().toSet())
        assertEquals(listOf("A", "A"), server.contents())
        assertEquals(setOf(SyncState.SYNCED), client.all().map { it.syncState }.toSet())
    }

    @Test
    fun `a title edit on a note the server did not change is simply uploaded`() = runBlocking {
        val a = server.put("A")
        client.sync()
        client.retitle(client.byContent("A").localId, "Mine")

        val report = success(client.sync())

        assertEquals(0, report.forked)
        assertEquals("Mine", server.titleOf(a))
    }

    @Test
    fun `a conflict found while pulling keeps the server text and uploads the local copy`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            client.edit(client.byContent("A").localId, "A edited")
            server.edit(a, "A from the server")

            val report = success(client.sync())

            assertEquals(1, report.forked)
            assertEquals(1, report.pushed)
            assertEquals(setOf("A from the server", "A edited"), server.contents().toSet())
            assertEquals(setOf("A", "A (conflicto 2026-10-04)"), server.titles().toSet())
            assertEquals(setOf(SyncState.SYNCED), client.all().map { it.syncState }.toSet())
        }

    @Test
    fun `a dirty note whose server version did not change is not resolved`() = runBlocking {
        server.put("A")
        server.put("B")
        client.sync()
        client.edit(client.byContent("A").localId, "A edited")
        server.fault = { if (it.startsWith("PUT")) Fault.Offline else null }
        server.put("C")
        client.checkpoints.current = SyncCheckpoint() // no pruning: A comes back in full

        val result = client.sync()

        assertTrue(result is SyncResult.Failed)
        val a = client.byContent("A edited")
        assertEquals(SyncState.DIRTY, a.syncState)
        assertEquals(a.lastSyncedEtag, a.etag)
        assertEquals(listOf("A", "B", "C"), server.contents())
    }

    @Test
    fun `an edit made while pulling is not overwritten and blocks the checkpoint`() = runBlocking {
        val a = server.put("A")
        client.sync()
        val saved = client.checkpoints.current
        server.edit(a, "A from the server")
        racy = { row ->
            racy = null
            client.dao.update(
                row.copy(content = "typed meanwhile", syncState = SyncState.DIRTY)
            )
        }

        val report = success(client.sync())

        assertTrue(report.skipped >= 1)
        assertEquals(saved, client.checkpoints.current)
        // The push then meets the server change (412): server text stays, typed text is a copy.
        assertEquals(1, report.forked)
        assertEquals(
            setOf("A from the server", "typed meanwhile"),
            server.contents().toSet()
        )
        assertEquals(setOf("A", "A (conflicto 2026-10-04)"), server.titles().toSet())
    }

    @Test
    fun `a conflict hit by an edit while pulling is skipped there and resolved by the push`() =
        runBlocking {
            val a = server.put("A")
            client.sync()
            client.edit(client.byContent("A").localId, "A edited")
            server.edit(a, "A from the server")
            racy = { row ->
                racy = null
                client.dao.update(row.copy(content = "A edited again"))
            }

            val report = success(client.sync())

            assertTrue(report.skipped >= 1)
            assertEquals(
                setOf("A from the server", "A edited again"),
                server.contents().toSet()
            )
            assertEquals(setOf("A", "A (conflicto 2026-10-04)"), server.titles().toSet())
        }

    @Test
    fun `identical text on both sides adopts the server etag`() = runBlocking {
        val a = server.put("A")
        client.sync()
        client.edit(client.byContent("A").localId, "A same")
        server.edit(a, "A same")

        client.sync()

        val note = client.byContent("A same")
        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals(server.etagOf(a), note.etag)
        assertEquals(note.etag, note.lastSyncedEtag)
        assertEquals(listOf("A same"), server.contents())
        assertTrue(server.requests.none { it.startsWith("PUT") })
    }

    @Test
    fun `a pull failure stops the run before any upload`() = runBlocking {
        client.create("Local")
        server.fault = { if (it == "GET") Fault.Status(HTTP_UNAUTHORIZED) else null }

        val result = client.sync()

        assertEquals(ApiError.Unauthorized, (result as SyncResult.Failed).error)
        assertEquals(listOf("GET"), server.requests)
        assertEquals(SyncState.NEW, client.all().single().syncState)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
