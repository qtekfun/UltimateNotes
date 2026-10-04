// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.content.Context
import android.util.Log
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.domain.list.buildNoteGroups
import com.qtekfun.ultimatenotes.domain.list.toListItem
import com.qtekfun.ultimatenotes.domain.search.RoomNoteSearcher
import com.qtekfun.ultimatenotes.domain.search.SearchQuery
import com.qtekfun.ultimatenotes.sync.FakeNotesServer
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpoint
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpointStore
import com.qtekfun.ultimatenotes.sync.queue.SyncEngine
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import java.time.Clock
import kotlin.system.measureNanoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T17b, how a big library behaves on the phone (SPEC §10): 500, 1 000 and 5 000 synthetic notes in
 * a real file database (not in memory): the first list, the folder tree, full-text search and the
 * first sync pull through the real sync engine against the in-process fake server (network and
 * JSON are not part of it). Prints `T17B scale ...` lines to logcat with the numbers; the asserts
 * only keep the numbers meaningful and the budgets are very loose (a regression guard, not a goal).
 */
class LibrarySizeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private class MemoryCheckpoints : SyncCheckpointStore {
        var current = SyncCheckpoint.NONE

        override suspend fun load() = current

        override suspend fun save(checkpoint: SyncCheckpoint) {
            current = checkpoint
        }

        override suspend fun clear() {
            current = SyncCheckpoint.NONE
        }
    }

    private fun openDatabase(name: String): UltimateNotesDatabase {
        context.deleteDatabase(name)
        return Room.databaseBuilder<UltimateNotesDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    private fun millis(block: () -> Unit) = measureNanoTime(block) / NANOS_PER_MILLI

    private fun median(samples: List<Double>) = samples.sorted()[samples.size / 2]

    private fun report(size: Int, metric: String, value: Double) {
        Log.i(T17B_TAG, "scale n=$size $metric = %.1f ms".format(value))
    }

    @Test
    @Suppress("LongMethod")
    fun readsAndSearchScaleWithTheLibrary() = runBlocking {
        for (size in SIZES) {
            val name = "t17b-read-$size.db"
            val database = openDatabase(name)
            try {
                val seed = millis {
                    runBlocking {
                        repeat(size) { database.noteDao().insert(SyntheticNotes.entity(it, size)) }
                    }
                }
                report(size, "seed (one insert each)", seed)
                val dao = database.noteDao()

                var listed = 0
                report(
                    size,
                    "first list query (observeAll, all rows with text)",
                    millis {
                        runBlocking { listed = dao.observeAll().first().size }
                    }
                )
                assertEquals(size, listed)
                val entities = dao.observeAll().first()
                report(
                    size,
                    "list rows + sections (toListItem, buildNoteGroups)",
                    median(
                        List(RUNS) {
                            millis {
                                val groups = buildNoteGroups(
                                    entities.map { it.toListItem() },
                                    FolderSelection.All,
                                    NoteSortOrder.MODIFIED,
                                    Clock.systemDefaultZone()
                                )
                                assertEquals(size, groups.sumOf { it.notes.size })
                            }
                        }
                    )
                )

                var tree = FolderOverview()
                report(
                    size,
                    "folder tree (counts query + build)",
                    median(
                        List(RUNS) {
                            millis {
                                runBlocking {
                                    val counts = dao.observeFolderCounts().first()
                                    tree =
                                        FolderOverview.from(
                                            counts,
                                            dao.observeFavorites().first().size
                                        )
                                }
                            }
                        }
                    )
                )
                assertEquals(size, tree.total)
                assertTrue("the library has folders", tree.folders.size > MIN_FOLDERS)

                val searcher = RoomNoteSearcher(database.noteSearchDao(), Dispatchers.Unconfined)
                val query = SearchQuery.parse(SyntheticNotes.SEARCH_WORD)!!
                var hits = 0
                val samples = List(SEARCH_RUNS) {
                    millis {
                        runBlocking {
                            searcher.search(query, null).test {
                                hits = awaitItem().size
                                cancelAndIgnoreRemainingEvents()
                            }
                        }
                    }
                }
                assertTrue("the query must find notes", hits > 0)
                report(size, "search first run ($hits hits)", samples.first())
                report(size, "search median", median(samples.drop(1)))
                val twoWords = SearchQuery.parse("groceries meeting")!!
                report(
                    size,
                    "search two words median",
                    median(
                        List(SEARCH_RUNS) {
                            millis {
                                runBlocking {
                                    searcher.search(twoWords, null).test {
                                        awaitItem()
                                        cancelAndIgnoreRemainingEvents()
                                    }
                                }
                            }
                        }
                    )
                )
                assertTrue("search is far too slow", samples.max() < SEARCH_BUDGET_MS)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test
    fun firstSyncPullScalesWithTheLibrary() = runBlocking {
        for (size in SIZES) {
            val name = "t17b-pull-$size.db"
            val database = openDatabase(name)
            try {
                val server = FakeNotesServer()
                repeat(size) {
                    server.put(
                        SyntheticNotes.body(it),
                        SyntheticNotes.category(it),
                        SyntheticNotes.favorite(it),
                        SyntheticNotes.title(it)
                    )
                }
                val engine = SyncEngine(
                    client = NotesClient(server, Dispatchers.Unconfined),
                    reads = database.noteSyncDao(),
                    writes = database.noteSyncWriteDao(),
                    checkpoints = MemoryCheckpoints(),
                    resolver = ConflictResolver(Clock.systemDefaultZone()),
                    io = Dispatchers.IO
                )
                var pulled = 0
                val first = millis {
                    val result = runBlocking { engine.sync() }
                    assertTrue("sync failed: $result", result is SyncResult.Success)
                    pulled = (result as SyncResult.Success).report.pulled
                }
                assertEquals(size, pulled)
                assertEquals(size, database.noteSyncDao().getAll().size)
                report(size, "first pull (Room file DB, in-process server)", first)
                Log.i(T17B_TAG, "scale n=$size pull = %.1f notes/s".format(size / (first / MILLIS)))
                val again = millis {
                    assertTrue(runBlocking { engine.sync() } is SyncResult.Success)
                }
                report(size, "second sync, nothing changed", again)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
    }

    private companion object {
        val SIZES = listOf(500, 1_000, 5_000)
        const val RUNS = 5
        const val SEARCH_RUNS = 8
        const val MIN_FOLDERS = 10
        const val SEARCH_BUDGET_MS = 5_000.0
        const val NANOS_PER_MILLI = 1_000_000.0
        const val MILLIS = 1_000.0
    }
}
