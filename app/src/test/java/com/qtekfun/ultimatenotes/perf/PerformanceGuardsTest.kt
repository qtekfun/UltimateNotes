// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.perf

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.domain.checklist.ChecklistParser
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.domain.list.buildNoteGroups
import com.qtekfun.ultimatenotes.domain.list.toListItem
import com.qtekfun.ultimatenotes.domain.markdown.MarkdownAnalyzer
import com.qtekfun.ultimatenotes.domain.markdown.StyleRuns
import com.qtekfun.ultimatenotes.domain.search.RoomNoteSearcher
import com.qtekfun.ultimatenotes.domain.search.SearchQuery
import com.qtekfun.ultimatenotes.sync.FakeNotesServer
import com.qtekfun.ultimatenotes.sync.SyncFixture
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Guards for SPEC section 10 ("list ready fast with 5 000 notes"). Each test prints what it
 * measured; the budget it asserts is far above the usual time (see ADR 0007), so a loaded CI
 * runner does not flake but an accidental quadratic blow-up still fails. These measure the JVM,
 * not a phone: the feel on a device is T17b. Run only these with `--tests '*PerformanceGuards*'`.
 */
@Tag("perf")
class PerformanceGuardsTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC)

    private val vocabulary = List(VOCABULARY) { "word${it}x" } +
        listOf("groceries", "meeting", "recipe", "invoice", "holiday", "garden", "budget")

    /** About [size] chars of varied Markdown, deterministic per [seed]. */
    private fun body(seed: Int, size: Int): String {
        val random = Random(seed)
        val text = StringBuilder()
        while (text.length < size) {
            val line = List(LINE_WORDS) { vocabulary[random.nextInt(vocabulary.size)] }
                .joinToString(" ")
            text.append(
                when (random.nextInt(LINE_KINDS)) {
                    0 -> "## $line"
                    1 -> "- [ ] $line"
                    2 -> "- [x] $line"
                    3 -> "> $line"
                    4 -> "**$line** and *$line* with `code`"
                    else -> line
                }
            ).append('\n')
        }
        return text.toString()
    }

    private fun category(index: Int) = listOf("", "Work", "Work/Ideas", "Home")[index % FOLDERS]

    private fun entity(index: Int) = NoteEntity(
        title = "Note $index ${vocabulary[index % vocabulary.size]}",
        content = body(index, NOTE_CHARS),
        category = category(index),
        favorite = index % FAVORITE_EVERY == 0,
        modified = clock.instant().epochSecond - index * SECONDS_APART
    )

    private fun median(samples: List<Double>) = samples.sorted()[samples.size / 2]

    private fun report(name: String, millis: Double, budget: Double) {
        println("PERF $name: median %.1f ms (budget %.0f ms)".format(millis, budget))
        assertTrue(millis < budget, "$name took $millis ms, budget $budget ms")
    }

    /** Median of [RUNS] timed runs after a warm-up. */
    private fun guard(name: String, budgetMillis: Double, block: () -> Unit) {
        repeat(WARMUP) { block() }
        report(name, median(List(RUNS) { measureNanoTime(block) / NANOS_PER_MILLI }), budgetMillis)
    }

    @Test
    fun `mapping and grouping 5000 notes for the list is fast`() {
        val entities = List(NOTES) { entity(it) }
        guard("list 5000 (toListItem + buildNoteGroups)", LIST_BUDGET) {
            val groups = buildNoteGroups(
                entities.map { it.toListItem() },
                FolderSelection.All,
                NoteSortOrder.MODIFIED,
                clock
            )
            assertEquals(NOTES, groups.sumOf { it.notes.size })
        }
    }

    @Test
    fun `full-text search over 5000 stored notes is fast`() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val dao = database.noteDao()
            repeat(NOTES) { dao.insert(entity(it)) }
            val searcher = RoomNoteSearcher(database.noteSearchDao(), Dispatchers.Unconfined)
            val query = SearchQuery.parse("groceries meeting")!!
            var hits = 0
            val samples = List(WARMUP + RUNS) {
                measureNanoTime {
                    searcher.search(query, null).test {
                        hits = awaitItem().size
                        cancelAndIgnoreRemainingEvents()
                    }
                } / NANOS_PER_MILLI
            }.drop(WARMUP)
            assertTrue(hits > 0, "the query must find something for the timing to mean anything")
            report("search 5000 ($hits hits)", median(samples), SEARCH_BUDGET)
        } finally {
            database.close()
        }
    }

    @Test
    fun `analyzing and styling a 50 KB note is fast`() {
        val note = body(1, BIG_NOTE_CHARS)
        guard("markdown analysis 50 KB", MARKDOWN_BUDGET) {
            val runs = StyleRuns.of(note, MarkdownAnalyzer.analyze(note))
            assertTrue(runs.isNotEmpty())
        }
    }

    @Test
    fun `parsing the checklist of a 50 KB note is fast`() {
        val note = body(2, BIG_NOTE_CHARS)
        val expected = note.lines().count { it.startsWith("- [") }
        guard("checklist parse 50 KB", CHECKLIST_BUDGET) {
            assertEquals(expected, ChecklistParser.parse(note).size)
        }
    }

    @Test
    fun `the first sync of 5000 notes is fast`() = runBlocking {
        val server = FakeNotesServer()
        repeat(NOTES) { server.put(body(it, NOTE_CHARS), category = category(it)) }
        val client = SyncFixture(server)
        try {
            val nanos = measureNanoTime {
                val result = client.sync()
                assertTrue(result is SyncResult.Success, "sync failed: $result")
                assertEquals(NOTES, result.report.pulled)
            }
            assertEquals(NOTES, client.all().size)
            report("sync pull 5000", nanos / NANOS_PER_MILLI, SYNC_BUDGET)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val NOTES = 5_000
        const val NOTE_CHARS = 2_000
        const val BIG_NOTE_CHARS = 50_000
        const val VOCABULARY = 400
        const val LINE_WORDS = 9
        const val LINE_KINDS = 8
        const val FOLDERS = 4
        const val FAVORITE_EVERY = 50
        const val SECONDS_APART = 600
        const val WARMUP = 3
        const val RUNS = 9
        const val NANOS_PER_MILLI = 1_000_000.0
        const val LIST_BUDGET = 500.0
        const val SEARCH_BUDGET = 1_000.0
        const val MARKDOWN_BUDGET = 1_000.0
        const val CHECKLIST_BUDGET = 500.0
        const val SYNC_BUDGET = 120_000.0
    }
}
