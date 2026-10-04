// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.markdown.StyleRole

/**
 * Makes the checklist boxes of the field tappable. The field is one piece of text, so there is no
 * checkbox composable to click: a tap is hit-tested against the box glyph's rectangle in the text
 * layout (offsets of the drawn text equal source offsets, see [MarkdownStyler]) and flips one
 * character of the source, as a normal undoable edit.
 *
 * The touch target is stretched sideways to about 48 dp. It watches the Initial pass and only
 * consumes a gesture that starts on a box, so placing the caret, selecting and scrolling work as
 * usual everywhere else.
 *
 * @param layout the field's last text layout (positions relative to this modifier's bounds).
 * @param scrollOffset how far the field is scrolled, in pixels.
 */
fun Modifier.toggleCheckboxOnTap(
    state: TextFieldState,
    source: StyleRunSource,
    layout: () -> TextLayoutResult?,
    scrollOffset: () -> Int,
    enabled: Boolean
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(state, source) {
        val reach = TARGET_REACH.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val box = boxAt(down.position, state, source, layout(), scrollOffset(), reach)
                ?: return@awaitEachGesture
            down.consume()
            val up = waitForUpOrCancellation(PointerEventPass.Initial)
            val quick = up != null &&
                up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                (up.position - down.position).getDistance() <= viewConfiguration.touchSlop
            if (quick) {
                up.consume()
                state.toggleChecklistBox(box)
            }
        }
    }
}

private fun boxAt(
    position: Offset,
    state: TextFieldState,
    source: StyleRunSource,
    layout: TextLayoutResult?,
    scroll: Int,
    reach: Float
): TextRange? {
    if (layout == null) return null
    val text = state.text.toString()
    val point = position + Offset(0f, scroll.toFloat())
    return source.runsFor(text)
        .filter { it.role is StyleRole.Checkbox && it.range.start < layout.layoutInput.text.length }
        .firstOrNull { run ->
            val glyph = layout.getBoundingBox(run.range.start)
            Rect(glyph.left - reach, glyph.top, glyph.right + reach, glyph.bottom).contains(point)
        }?.range
}

private val TARGET_REACH = 16.dp
