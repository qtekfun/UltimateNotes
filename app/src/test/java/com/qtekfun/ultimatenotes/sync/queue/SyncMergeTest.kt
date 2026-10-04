// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.FakeNotesServer
import com.qtekfun.ultimatenotes.sync.SyncFixture
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.opentest4j.AssertionFailedError

/**
 * Three-way merge property (ADR 0011). Two clients share one synced note and, offline, each
 * changes a random subset of its text, title, folder and favorite. Whenever the text and the title
 * were not both changed on the two sides, nothing may clash: after syncing until quiet there is
 * still exactly one note, no "(conflicto ...)" copy exists, and
 *
 * - a text or title changed by only one client has that client's value everywhere;
 * - a folder or favorite changed by only one client has that client's value everywhere;
 * - one changed by both is the same everywhere and is one of the two values (the one of the client
 *   that synced last wins, i.e. the local value of whoever had to merge).
 *
 * When both change the text (or both the title) to different values, the clients must still
 * converge and neither text may be lost, but a copy is expected.
 */
class SyncMergeTest {
    private enum class Field { CONTENT, TITLE, CATEGORY, FAVORITE }

    private class Scenario(seed: Long) {
        val random = Random(seed)
        val server = FakeNotesServer()
        val a = SyncFixture(server)
        val b = SyncFixture(server)
        val changedByA = Field.entries.filter { random.nextBoolean() }.toSet()
        val changedByB = Field.entries.filter { random.nextBoolean() }.toSet()
        val bothChangedText = Field.CONTENT in changedByA && Field.CONTENT in changedByB
        val bothChangedTitle = Field.TITLE in changedByA && Field.TITLE in changedByB
        val clashes = bothChangedText || bothChangedTitle
    }

    private suspend fun SyncFixture.change(localId: Long, fields: Set<Field>, who: String) {
        if (Field.CONTENT in fields) edit(localId, "text by $who")
        if (Field.TITLE in fields) retitle(localId, "title by $who")
        if (Field.CATEGORY in fields) move(localId, "folder $who")
        if (Field.FAVORITE in fields) favorite(localId, true)
    }

    private suspend fun quiet(scenario: Scenario) {
        val order = if (scenario.random.nextBoolean()) {
            listOf(scenario.a, scenario.b)
        } else {
            listOf(scenario.b, scenario.a)
        }
        repeat(MAX_PASSES) {
            order.forEach { it.sync() }
        }
    }

    private fun run(seed: Long) = runBlocking {
        val s = Scenario(seed)
        try {
            val localId = s.a.create("original text", title = "original title")
            s.a.sync()
            s.b.sync()
            val remote = s.b.all().single().localId
            s.a.change(localId, s.changedByA, "A")
            s.b.change(remote, s.changedByB, "B")

            quiet(s)

            listOf(s.a, s.b).forEach { client ->
                fail(seed, client.all().all { it.syncState == SyncState.SYNCED }) {
                    "a client has unsynced rows"
                }
            }
            fail(seed, s.b.visible() == s.a.visible()) { "the clients differ" }
            fail(
                seed,
                s.b.visible() ==
                    s.server.snapshot().sortedWith(
                        compareBy({
                            it.first
                        }, { it.second }, { it.third })
                    )
            ) {
                "the clients differ from the server"
            }
            val titles = s.server.titles()
            if (s.clashes) {
                val texts = s.server.contents().joinToString("\n")
                fail(seed, !s.bothChangedText || ("text by A" in texts && "text by B" in texts)) {
                    "a clashing text was lost"
                }
                fail(seed, !s.bothChangedTitle || titles.any { "title by A" in it }) {
                    "a clashing title was lost"
                }
                fail(seed, !s.bothChangedTitle || titles.any { "title by B" in it }) {
                    "a clashing title was lost"
                }
            } else {
                checkMerged(s, seed)
            }
        } finally {
            s.a.close()
            s.b.close()
        }
    }

    private fun checkMerged(s: Scenario, seed: Long) {
        fail(seed, s.server.ids().size == 1) { "a conflict copy was created" }
        fail(seed, s.server.titles().none { "conflicto" in it }) { "a conflict copy was created" }
        val (content, category, favorite) = s.server.snapshot().single()
        val title = s.server.titles().single()
        val aContent = Field.CONTENT in s.changedByA
        val bContent = Field.CONTENT in s.changedByB
        val expectedText = when {
            aContent -> "text by A"
            bContent -> "text by B"
            else -> "original text"
        }
        fail(seed, content == expectedText) { "text not merged" }
        val aTitle = Field.TITLE in s.changedByA
        val bTitle = Field.TITLE in s.changedByB
        val expectedTitle = when {
            aTitle -> "title by A"
            bTitle -> "title by B"
            else -> "original title"
        }
        fail(seed, title == expectedTitle) { "title not merged" }
        fail(seed, category in expectedFolders(s)) { "folder not merged" }
        fail(seed, favorite == (Field.FAVORITE in s.changedByA || Field.FAVORITE in s.changedByB)) {
            "favorite not merged"
        }
    }

    private fun expectedFolders(s: Scenario): Set<String> {
        val a = Field.CATEGORY in s.changedByA
        val b = Field.CATEGORY in s.changedByB
        return when {
            a && b -> setOf("folder A", "folder B")
            a -> setOf("folder A")
            b -> setOf("folder B")
            else -> setOf("")
        }
    }

    private fun fail(seed: Long, ok: Boolean, message: () -> String) {
        if (!ok) throw AssertionFailedError("seed $seed: ${message()}")
    }

    @TestFactory
    fun `fields changed on different sides merge without a conflict copy`() =
        (1L..SEEDS).map { seed -> dynamicTest("seed $seed") { run(seed) } }

    @Test
    fun `the seeds cover both the clashing and the merging scenarios`() {
        val scenarios = (1L..SEEDS).map {
            Scenario(it).also { s ->
                s.a.close()
                s.b.close()
            }
        }

        assertTrue(scenarios.any { it.clashes })
        assertTrue(scenarios.count { !it.clashes } > SEEDS / 4)
    }

    private companion object {
        const val SEEDS = 200L
        const val MAX_PASSES = 4
    }
}
