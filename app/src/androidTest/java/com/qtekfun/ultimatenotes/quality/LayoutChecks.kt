// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/** Tag of the numbers the T17b tests print to logcat (never any note content). */
const val T17B_TAG = "T17B"

/** Helpers that check what a layout must guarantee: reachable, not clipped, not overlapping. */
class LayoutChecks(private val compose: ComposeTestRule) {
    /** The app window: the biggest root (popups and drag handles are roots of their own). */
    private fun window(): Rect = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        .map { it.boundsInRoot }.maxBy { it.width * it.height }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    /** Every control is fully inside the window (not clipped by the screen edge). */
    fun assertInsideWindow(
        screen: String,
        vararg controls: Pair<String, SemanticsNodeInteraction>
    ) {
        val screenBounds = window()
        for ((name, control) in controls) {
            val bounds = control.bounds()
            Log.i(T17B_TAG, "$screen: $name at $bounds (window $screenBounds)")
            assertTrue(
                "$screen: $name is clipped by the window: $bounds vs $screenBounds",
                bounds.left >= screenBounds.left - EDGE_TOLERANCE &&
                    bounds.top >= screenBounds.top - EDGE_TOLERANCE &&
                    bounds.right <= screenBounds.right + EDGE_TOLERANCE &&
                    bounds.bottom <= screenBounds.bottom + EDGE_TOLERANCE
            )
            assertTrue("$screen: $name has no size: $bounds", bounds.width > 0 && bounds.height > 0)
        }
    }

    /** No two of the controls cover each other (a shared edge is fine). */
    fun assertNoOverlap(screen: String, vararg controls: Pair<String, SemanticsNodeInteraction>) {
        val boxes = controls.map { (name, node) -> name to node.bounds() }
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                val overlap = boxes[i].second.intersect(boxes[j].second)
                assertTrue(
                    "$screen: ${boxes[i].first} overlaps ${boxes[j].first} ($overlap)",
                    overlap.width <= OVERLAP_TOLERANCE || overlap.height <= OVERLAP_TOLERANCE
                )
            }
        }
    }

    /** Text nodes (merged tree) with an ellipsized line, i.e. whose text is cut off with "...". */
    fun overflowing(nodes: SemanticsNodeInteractionCollection): List<String> =
        nodes.fetchSemanticsNodes().mapNotNull { node ->
            val layout = textLayoutOf(node) ?: return@mapNotNull null
            val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            Log.i(
                T17B_TAG,
                "text len=${layout.layoutInput.text.length} lines=${layout.lineCount} " +
                    "size=${layout.size} overflow=${layout.hasVisualOverflow} ellipsized=$ellipsized"
            )
            if (ellipsized) {
                "lines=${layout.lineCount} size=${layout.size} len=${layout.layoutInput.text.length}"
            } else {
                null
            }
        }

    private fun textLayoutOf(node: SemanticsNode): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        return if (action != null && action(results)) results.firstOrNull() else null
    }

    private companion object {
        const val EDGE_TOLERANCE = 1f
        const val OVERLAP_TOLERANCE = 1f
    }
}
