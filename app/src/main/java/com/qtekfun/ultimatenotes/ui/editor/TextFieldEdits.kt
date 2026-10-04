// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange as FieldRange
import com.qtekfun.ultimatenotes.domain.TextRange as DomainRange
import com.qtekfun.ultimatenotes.domain.checklist.ChecklistParser
import com.qtekfun.ultimatenotes.domain.markdown.EditResult

/** The selection as a domain range, whichever way it was dragged. */
fun TextFieldState.domainSelection(): DomainRange = DomainRange(selection.min, selection.max)

/**
 * Applies a pure edit result as one undoable replacement of just the changed part, so the rest
 * of the text (and its undo history) is left alone.
 */
fun TextFieldState.applyResult(result: EditResult) {
    val edit = result.toEdit(text.toString())
    edit {
        replace(edit.range.start, edit.range.end, edit.replacement)
        selection = FieldRange(edit.selection.start, edit.selection.end)
    }
}

/**
 * Toggles the checklist box at [box] by changing exactly one character. Returns false if there is
 * no box there any more (the text moved on since [box] was computed).
 */
fun TextFieldState.toggleChecklistBox(box: DomainRange): Boolean {
    val current = text.toString()
    val toggled = runCatching { ChecklistParser.toggle(current, box) }.getOrNull() ?: return false
    applyResult(EditResult(toggled, domainSelection()))
    return true
}

/** Toggles the checklist item on the caret's line, if it is one. Returns whether it was. */
fun TextFieldState.toggleChecklistAtCaret(): Boolean {
    val current = text.toString()
    val toggled = ChecklistParser.toggleAtOffset(current, selection.min) ?: return false
    applyResult(EditResult(toggled, domainSelection()))
    return true
}
