// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.FakeNotesServer
import com.qtekfun.ultimatenotes.sync.FakeNotesServer.Fault
import com.qtekfun.ultimatenotes.sync.SyncFixture
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError

/**
 * Convergence property: two clients make random edits, deletes, moves and favorites, interleaved
 * with syncs that randomly fail or are interrupted by local edits, against a faithful ETag fake
 * server. Once the failures stop and both clients sync until quiet, they must
 *
 * 1. converge: both clients and the server hold exactly the same notes, all of them synced;
 * 2. lose no text: every text a user typed survives somewhere unless a client that had seen that
 *    text later overwrote or deleted it itself ("superseded"). Typed texts carry a unique token.
 *
 * The seed is in the test name: a failure is reproducible by rerunning that seed.
 */
class SyncConvergenceTest {
    private class World(seed: Long, wrapDao: (NoteSyncWriteDao) -> NoteSyncWriteDao) {
        val random = Random(seed)
        val server = FakeNotesServer()
        val clients = listOf(SyncFixture(server, wrapDao), SyncFixture(server, wrapDao))
        private var tokens = 0
        private var notes = 0

        /** Every token ever typed and every token a client saw before overwriting or deleting it. */
        val typed = mutableSetOf<String>()
        val superseded = mutableSetOf<String>()

        fun newText(key: Int) = "Note $key\n${nextToken()}"

        private fun nextToken() = "v${tokens++}".also { typed += it }

        fun newKey() = notes++

        fun tokensIn(text: String) = TOKEN.findAll(text).map { it.value }.toSet()

        suspend fun userOp(client: SyncFixture) {
            val live = client.all().filter { it.syncState != SyncState.DELETED }
            val note = live.randomOrNull(random)
            when {
                note == null || random.nextInt(CREATE_ONE_IN) == 0 -> {
                    val key = newKey()
                    client.create(newText(key), category = FOLDERS.random(random))
                }

                else -> when (random.nextInt(OP_KINDS)) {
                    0, 1 -> edit(client, note)

                    2 -> client.move(note.localId, FOLDERS.random(random))

                    3 -> client.favorite(note.localId, !note.favorite)

                    else -> {
                        superseded += tokensIn(note.content)
                        client.delete(note.localId)
                    }
                }
            }
        }

        private suspend fun edit(client: SyncFixture, note: NoteEntity) {
            superseded += tokensIn(note.content)
            val key = KEY.find(note.content)?.groupValues?.get(1)?.toInt() ?: newKey()
            client.edit(note.localId, newText(key))
        }

        fun injectFaults(failureRate: Int, inFlightEditRate: Int, syncing: () -> SyncFixture) {
            server.fault = {
                when {
                    failureRate == 0 || random.nextInt(PERCENT) >= failureRate -> null

                    else -> listOf<Fault>(
                        Fault.Offline,
                        Fault.Status(HTTP_SERVER),
                        Fault.Status(HTTP_AUTH)
                    )
                        .random(random)
                }
            }
            server.onRequest = {
                if (random.nextInt(PERCENT) < inFlightEditRate) userOp(syncing())
            }
        }

        fun calm() {
            server.fault = { null }
            server.onRequest = {}
        }
    }

    private fun run(
        seed: Long,
        wrapDao: (NoteSyncWriteDao) -> NoteSyncWriteDao = {
            it
        }
    ) = runBlocking {
        val world = World(seed, wrapDao)
        var syncing = world.clients[0]
        world.injectFaults(failureRate = FAILURE_PERCENT, inFlightEditRate = IN_FLIGHT_PERCENT) {
            syncing
        }
        try {
            repeat(STEPS) {
                val client = world.clients[world.random.nextInt(2)]
                if (world.random.nextInt(PERCENT) < SYNC_PERCENT) {
                    syncing = client
                    client.sync()
                } else {
                    world.userOp(client)
                }
            }
            world.calm()
            converge(world, seed)
            check(world, seed)
        } finally {
            world.clients.forEach { it.close() }
        }
    }

    private suspend fun converge(world: World, seed: Long) {
        repeat(MAX_PASSES) {
            val reports = world.clients.map { it.sync() }
            if (reports.all { it == SyncResult.Success(SyncReport()) }) return
        }
        throw AssertionFailedError("seed $seed: clients never went quiet")
    }

    private suspend fun check(world: World, seed: Long) {
        val serverNotes = world.server.snapshot().sortedBy { it.first }
        world.clients.forEachIndexed { index, client ->
            val rows = client.all()
            fail(seed, rows.all { it.syncState == SyncState.SYNCED }) {
                "client $index has unsynced rows: ${rows.map { it.syncState }}"
            }
            val visible = client.visible()
            fail(seed, visible == serverNotes) {
                "client $index differs from the server: ${visible.size} vs ${serverNotes.size} notes"
            }
        }
        val texts = world.server.contents().joinToString("\n")
        val lost = (world.typed - world.superseded).filterNot { it in world.tokensIn(texts) }
        fail(seed, lost.isEmpty()) { "typed text lost: $lost" }
    }

    private fun fail(seed: Long, ok: Boolean, message: () -> String) {
        if (!ok) throw AssertionFailedError("seed $seed: ${message()}")
    }

    @TestFactory
    fun `two clients always converge and never lose text`() = (1L..SEEDS).map { seed ->
        dynamicTest("seed $seed") { run(seed) }
    }

    /** Guards the oracle itself: if a conflict copy is silently dropped, the property must fail. */
    @Test
    fun `the property detects a resolver that drops the local text`() {
        class LosingDao(private val real: NoteSyncWriteDao) : NoteSyncWriteDao by real {
            override suspend fun forkIfUnchanged(
                expected: NoteEntity,
                replacement: NoteEntity,
                copy: NoteEntity
            ): NoteEntity? = if (replaceIfUnchanged(expected, replacement)) copy else null
        }

        assertThrows<AssertionFailedError> {
            (1L..SEEDS).forEach { run(it) { dao -> LosingDao(dao) } }
        }
    }

    private companion object {
        const val SEEDS = 120L
        const val STEPS = 60
        const val MAX_PASSES = 12
        const val PERCENT = 100
        const val SYNC_PERCENT = 40
        const val FAILURE_PERCENT = 12
        const val IN_FLIGHT_PERCENT = 8
        const val CREATE_ONE_IN = 6
        const val OP_KINDS = 5
        const val HTTP_SERVER = 500
        const val HTTP_AUTH = 401
        val FOLDERS = listOf("", "Work", "Home", "Work/Reports")
        val TOKEN = Regex("""\bv\d+\b""")
        val KEY = Regex("""^Note (\d+)""")
    }
}
