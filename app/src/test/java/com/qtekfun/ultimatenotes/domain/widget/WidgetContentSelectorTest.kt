// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetContentSelectorTest {
    private val selector = WidgetContentSelector()

    private fun item(
        id: Long,
        modified: Long,
        favorite: Boolean = false,
        title: String = "n$id",
        preview: String = ""
    ) = NoteListItem(id, title, preview, "", favorite, Instant.ofEpochSecond(modified))

    private fun shown(items: List<NoteListItem>) =
        (selector.select(signedIn = true, hidden = false, notes = items) as WidgetContent.Notes)
            .notes

    @Test
    fun `favorites come first then the most recently modified`() {
        val notes = shown(
            listOf(
                item(1, modified = 100),
                item(2, modified = 300),
                item(3, modified = 50, favorite = true),
                item(4, modified = 200, favorite = true)
            )
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), notes.map { it.localId })
    }

    @Test
    fun `notes modified at the same second keep a stable order by local id`() {
        val notes = shown(listOf(item(1, 100), item(3, 100), item(2, 100)))
        assertEquals(listOf(3L, 2L, 1L), notes.map { it.localId })
    }

    @Test
    fun `only the first ten notes are kept`() {
        val notes = shown((1L..25L).map { item(it, modified = it) })
        assertEquals(WidgetContentSelector.MAX_NOTES, notes.size)
        assertEquals(25L, notes.first().localId)
        assertEquals(16L, notes.last().localId)
    }

    @Test
    fun `the preview is one line`() {
        val note = shown(listOf(item(1, 1, preview = "first\n  second\t third")))[0]
        assertEquals("first second third", note.preview)
    }

    @Test
    fun `a long preview is cut to the limit`() {
        val long = "word ".repeat(40)
        val cut = shown(listOf(item(1, 1, preview = long)))[0].preview
        assertEquals(WidgetContentSelector.PREVIEW_LENGTH - 1, cut.length)
        assertTrue(long.startsWith(cut))
    }

    @Test
    fun `a preview never ends in half of a surrogate pair`() {
        val emoji = "😀"
        val preview = "a".repeat(WidgetContentSelector.PREVIEW_LENGTH - 1) + emoji
        val cut = shown(listOf(item(1, 1, preview = preview)))[0].preview
        assertEquals("a".repeat(WidgetContentSelector.PREVIEW_LENGTH - 1), cut)
    }

    @Test
    fun `a preview of exactly the limit is kept whole`() {
        val preview = "b".repeat(WidgetContentSelector.PREVIEW_LENGTH)
        assertEquals(preview, shown(listOf(item(1, 1, preview = preview)))[0].preview)
    }

    @Test
    fun `the stored title is trimmed and an empty one stays empty for the UI to localize`() {
        val notes = shown(listOf(item(1, 2, title = "  Plan "), item(2, 1, title = " ")))
        assertEquals(listOf("Plan", ""), notes.map { it.title })
    }

    @Test
    fun `the favorite flag is carried to the row`() {
        assertTrue(shown(listOf(item(1, 1, favorite = true)))[0].favorite)
        assertFalse(shown(listOf(item(1, 1)))[0].favorite)
    }

    @Test
    fun `no notes is the empty state`() {
        assertEquals(
            WidgetContent.Empty,
            selector.select(true, hidden = false, notes = emptyList())
        )
    }

    @Test
    fun `signed out shows the sign in state whatever else is true`() {
        val notes = listOf(item(1, 1))
        assertEquals(WidgetContent.SignedOut, selector.select(false, hidden = false, notes = notes))
        assertEquals(WidgetContent.SignedOut, selector.select(false, hidden = true, notes = notes))
    }

    @Test
    fun `hidden shows the locked state and no note data`() {
        val notes = listOf(item(1, 1, title = "secret"))
        assertEquals(WidgetContent.Locked, selector.select(true, hidden = true, notes = notes))
    }

    @Test
    fun `content is hidden when the lock is enabled even if the app is unlocked`() {
        assertTrue(selector.isHidden(lockEnabled = true, locked = false))
        assertTrue(selector.isHidden(lockEnabled = true, locked = true))
        assertTrue(selector.isHidden(lockEnabled = false, locked = true))
        assertFalse(selector.isHidden(lockEnabled = false, locked = false))
    }
}
