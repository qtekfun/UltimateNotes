// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.privacy

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NoteWriteDto
import com.qtekfun.ultimatenotes.data.auth.dto.LoginResultDto
import com.qtekfun.ultimatenotes.data.auth.dto.LoginStartDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.entity.NoteFtsEntity
import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.editor.CreatedNote
import com.qtekfun.ultimatenotes.domain.export.ExportBlock
import com.qtekfun.ultimatenotes.domain.export.ExportBlockKind
import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import com.qtekfun.ultimatenotes.domain.markdown.EditResult
import com.qtekfun.ultimatenotes.domain.markdown.SegmentKind
import com.qtekfun.ultimatenotes.domain.markdown.TextEdit
import com.qtekfun.ultimatenotes.domain.search.Highlighted
import com.qtekfun.ultimatenotes.domain.search.SearchResult
import com.qtekfun.ultimatenotes.domain.widget.WidgetNote
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Anything that can end up in a crash report or a log must not spell out a note or a secret. */
class RedactedToStringTest {
    private val secret = "TOP-SECRET-xyz"

    private fun assertRedacted(value: Any) {
        assertFalse(value.toString().contains(secret), "leaks: $value")
    }

    @Test
    fun `note models never print their title, folder or text`() {
        assertRedacted(NoteEntity(title = secret, category = secret, content = secret))
        assertRedacted(NoteFtsEntity(secret, secret))
        assertRedacted(NoteDto(1, title = secret, category = secret, content = secret))
        assertRedacted(NoteWriteDto(title = secret, category = secret, content = secret))
        assertRedacted(NoteListItem(1, secret, secret, secret, false, Instant.EPOCH))
        assertRedacted(WidgetNote(1, secret, secret, false))
        assertRedacted(Highlighted(secret, emptyList()))
        assertRedacted(
            SearchResult(
                1,
                Highlighted(secret, emptyList()),
                Highlighted(secret, emptyList()),
                secret,
                false,
                Instant.EPOCH
            )
        )
        assertRedacted(CreatedNote(1, secret))
    }

    @Test
    fun `editor and export models never print note text`() {
        val range = TextRange(0, 0)
        assertRedacted(EditResult(secret, range))
        assertRedacted(TextEdit(range, secret, range))
        assertRedacted(ExportBlock(ExportBlockKind.Body, secret))
        assertRedacted(SegmentKind.Link(secret))
    }

    @Test
    fun `login models never print the app password or the one-time token`() {
        assertRedacted(LoginResultDto("https://cloud.example.org", "alice", secret))
        assertRedacted(LoginStartDto.Poll(secret, "https://cloud.example.org/poll"))
        assertRedacted(LoginStartDto(LoginStartDto.Poll(secret, "e"), "https://x/$secret"))
    }
}
