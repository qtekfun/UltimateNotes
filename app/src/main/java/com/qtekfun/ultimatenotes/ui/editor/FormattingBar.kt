// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.markdown.BlockKind
import com.qtekfun.ultimatenotes.domain.markdown.FormatAction
import com.qtekfun.ultimatenotes.domain.markdown.InlineStyle
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme

/** One button of the bar: how it looks, what it says to a screen reader, and what it does. */
private class FormatButton(
    val label: String,
    val style: TextStyle,
    val description: Int,
    val action: FormatAction
)

private val BUTTONS = listOf(
    FormatButton(
        "B",
        TextStyle(fontWeight = FontWeight.Bold),
        R.string.format_bold,
        FormatAction.Inline(InlineStyle.Bold)
    ),
    FormatButton(
        "I",
        TextStyle(fontStyle = FontStyle.Italic),
        R.string.format_italic,
        FormatAction.Inline(InlineStyle.Italic)
    ),
    FormatButton(
        "S",
        TextStyle(textDecoration = TextDecoration.LineThrough),
        R.string.format_strike,
        FormatAction.Inline(InlineStyle.Strike)
    ),
    FormatButton(
        "H",
        TextStyle(fontWeight = FontWeight.Bold),
        R.string.format_heading,
        FormatAction.Heading
    ),
    FormatButton("•", TextStyle(), R.string.format_bullet, FormatAction.Block(BlockKind.Bullet)),
    FormatButton(
        "1.",
        TextStyle(),
        R.string.format_numbered,
        FormatAction.Block(BlockKind.Numbered)
    ),
    FormatButton(
        "☑",
        TextStyle(),
        R.string.format_checklist,
        FormatAction.Block(BlockKind.Checklist)
    ),
    FormatButton(
        "“",
        TextStyle(fontWeight = FontWeight.Bold),
        R.string.format_quote,
        FormatAction.Block(BlockKind.Quote)
    ),
    FormatButton("↗", TextStyle(), R.string.format_link, FormatAction.Link),
    FormatButton(
        "</>",
        TextStyle(fontFamily = FontFamily.Monospace),
        R.string.format_code,
        FormatAction.Inline(InlineStyle.Code)
    )
)

/**
 * The formatting bar above the keyboard: 48 dp touch targets in a row that scrolls sideways when
 * the screen (or the font) is too big for all of them. Text labels, since the icon set in use has
 * no formatting icons; each button has a spoken description.
 */
@Composable
fun FormattingBar(
    onAction: (FormatAction) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (() -> Unit)? = null
) {
    val description = stringResource(R.string.format_toolbar)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            if (onOpenLink != null) {
                val openDescription = stringResource(R.string.format_open_link)
                TextButton(
                    onClick = onOpenLink,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = openDescription }
                ) { Text(openDescription) }
            }
            for (button in BUTTONS) {
                val buttonDescription = stringResource(button.description)
                IconButton(
                    onClick = { onAction(button.action) },
                    modifier = Modifier.semantics { contentDescription = buttonDescription }
                ) {
                    Text(
                        button.label,
                        style = button.style,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FormattingBarPreview() {
    UltimateNotesTheme { FormattingBar(onAction = {}) }
}

@Preview(showBackground = true)
@Composable
private fun FormattingBarOpenLinkPreview() {
    UltimateNotesTheme { FormattingBar(onAction = {}, onOpenLink = {}) }
}
