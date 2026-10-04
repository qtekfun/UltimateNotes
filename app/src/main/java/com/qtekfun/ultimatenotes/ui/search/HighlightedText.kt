// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.qtekfun.ultimatenotes.domain.search.Highlighted

/**
 * [highlighted] with its matches in bold on a tinted background. Bold keeps them visible
 * without relying on color alone.
 */
@Composable
fun rememberHighlightedText(highlighted: Highlighted): AnnotatedString {
    val style = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        background = MaterialTheme.colorScheme.secondaryContainer
    )
    return remember(highlighted, style) { annotate(highlighted, style) }
}

internal fun annotate(highlighted: Highlighted, style: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(highlighted.text)
        highlighted.ranges.forEach { addStyle(style, it.start, it.end) }
    }
