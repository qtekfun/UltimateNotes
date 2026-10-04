// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange as FieldRange
import com.qtekfun.ultimatenotes.domain.TextRange as DomainRange
import com.qtekfun.ultimatenotes.domain.markdown.continueList

/**
 * Enter on a list, checklist or quote line continues it (or ends it on an empty item). It only
 * reacts to a lone newline typed at the caret; everything else passes through untouched.
 */
@OptIn(ExperimentalFoundationApi::class)
object ContinueListOnEnter : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (changes.changeCount != 1) return
        val inserted = changes.getRange(0)
        val replaced = changes.getOriginalRange(0)
        val isLoneNewline = replaced.length == 0 && inserted.length == 1 &&
            asCharSequence()[inserted.start] == '\n' && originalSelection.collapsed &&
            originalSelection.start == replaced.start
        if (!isLoneNewline) return
        val before = originalText.toString()
        val result = continueList(before, DomainRange(replaced.start, replaced.start)) ?: return
        val edit = result.toEdit(before)
        revertAllChanges()
        replace(edit.range.start, edit.range.end, edit.replacement)
        selection = FieldRange(edit.selection.start, edit.selection.end)
    }
}
