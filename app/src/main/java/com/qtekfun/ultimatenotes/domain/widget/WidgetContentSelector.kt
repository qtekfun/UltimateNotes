// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import javax.inject.Inject

/**
 * Pure decisions of the widget (SPEC §8): which state to show, which notes and in what order.
 * Contains no Android code and never logs.
 */
class WidgetContentSelector @Inject constructor() {
    /**
     * The widget never shows content while the lock is enabled, even when the app was unlocked a
     * moment ago: an unlocked session outlives the app's time on screen (the re-lock timeout), and
     * the widget lives on the home screen. [locked] covers a lock engaged right now.
     */
    fun isHidden(lockEnabled: Boolean, locked: Boolean): Boolean = lockEnabled || locked

    /** Signed-out wins over locked: with no account there is nothing to protect. */
    fun select(signedIn: Boolean, hidden: Boolean, notes: List<NoteListItem>): WidgetContent =
        when {
            !signedIn -> WidgetContent.SignedOut

            hidden -> WidgetContent.Locked

            else -> toWidgetNotes(notes).let {
                if (it.isEmpty()) WidgetContent.Empty else WidgetContent.Notes(it)
            }
        }

    /** Favorites first, then the most recently modified; at most [MAX_NOTES], one-line previews. */
    fun toWidgetNotes(notes: List<NoteListItem>): List<WidgetNote> = notes.sortedWith(
        compareByDescending<NoteListItem> { it.favorite }
            .thenByDescending { it.modified }
            .thenByDescending { it.localId }
    ).take(MAX_NOTES).map {
        WidgetNote(it.localId, it.title.trim(), oneLine(it.preview), it.favorite)
    }

    private fun oneLine(text: String): String {
        val flat = text.replace(WHITESPACE, " ").trim()
        if (flat.length <= PREVIEW_LENGTH) return flat
        val splitsPair = flat[PREVIEW_LENGTH - 1].isHighSurrogate()
        val end = if (splitsPair) PREVIEW_LENGTH - 1 else PREVIEW_LENGTH
        return flat.substring(0, end).trimEnd()
    }

    companion object {
        /** Notes the widget keeps; the layout shows as many as fit. */
        const val MAX_NOTES = 10
        const val PREVIEW_LENGTH = 80
        private val WHITESPACE = Regex("""\s+""")
    }
}
