// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.qtekfun.ultimatenotes.domain.link.LinkOpener
import com.qtekfun.ultimatenotes.domain.markdown.NoteLinks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/**
 * What the editor does with links (ADR 0013): the "Open link" button shows while the caret is in
 * an openable link, and in a read-only note a tap on link text opens it. In an editable note a tap
 * only places the caret. Everything that decides is in `domain` ([NoteLinks]); this only connects
 * it to the field and to the [opener].
 */
class EditorLinks(private val opener: LinkOpener) {
    /** The openable destination of the link the caret is in, or null (also for a selection). */
    fun linkAtCaret(text: CharSequence, selection: TextRange): String? =
        NoteLinks.openableAtCaret(text.toString(), selection.start, selection.end)

    /**
     * The destination under the caret of [text] each time it changes, null when there is none.
     * The analysis runs on [dispatcher]; a newer change cancels one still running.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun caretLinks(
        text: TextFieldState,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): Flow<String?> = snapshotFlow { text.text.toString() to text.selection }
        .distinctUntilChanged()
        .mapLatest { (content, selection) -> linkAtCaret(content, selection) }
        .flowOn(dispatcher)
        .distinctUntilChanged()

    /** Opens what [linkAtCaret] found (the button). */
    fun open(uri: String) = opener.open(uri)

    /**
     * A tap on the character at [index] (null if it was not on text). Opens the link there only in
     * a [readOnly] note; returns whether it did, so the caller can consume the tap.
     */
    fun tapped(text: CharSequence, index: Int?, readOnly: Boolean): Boolean {
        val uri = if (readOnly &&
            index != null
        ) {
            NoteLinks.openableAtChar(text.toString(), index)
        } else {
            null
        }
        uri?.let(opener::open)
        return uri != null
    }
}
