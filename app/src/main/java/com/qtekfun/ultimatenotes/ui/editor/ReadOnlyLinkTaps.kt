// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult

/**
 * In a read-only note, a quick tap on link text opens the link (ADR 0013). Does nothing when
 * [readOnly] is false, so in an editable note a tap only places the caret. The down event is
 * never consumed, so scrolling and selecting work as usual; only the up of a quick tap that
 * opened a link is.
 *
 * @param layout the field's last text layout (positions relative to this modifier's bounds).
 * @param scrollOffset how far the field is scrolled, in pixels.
 */
fun Modifier.openLinkOnTap(
    state: TextFieldState,
    links: EditorLinks,
    layout: () -> TextLayoutResult?,
    scrollOffset: () -> Int,
    readOnly: Boolean
): Modifier = if (!readOnly) {
    this
} else {
    pointerInput(state, links) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(PointerEventPass.Initial)
            val quick = up != null &&
                up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                (up.position - down.position).getDistance() <= viewConfiguration.touchSlop
            if (quick &&
                links.tapped(state.text, charAt(down.position, layout(), scrollOffset()), true)
            ) {
                up.consume()
            }
        }
    }
}

/** The index of the character drawn under [position], or null when the tap is not on text. */
private fun charAt(position: Offset, layout: TextLayoutResult?, scroll: Int): Int? {
    if (layout == null) return null
    val point = position + Offset(0f, scroll.toFloat())
    val length = layout.layoutInput.text.length
    val nearest = layout.getOffsetForPosition(point)
    // The offset is the boundary nearest the tap: the character is the one before it or after it.
    return listOf(nearest - 1, nearest).firstOrNull { index ->
        index in 0 until length && layout.getBoundingBox(index).contains(point)
    }
}
