// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme

/** Height of the bar plus its margins: the list keeps this much free at the bottom. */
val FloatingSearchBarHeight = 56.dp + 16.dp * 2

/**
 * The Apple Notes bottom bar (SPEC §7): a search capsule with the new-note button at its right.
 * It sits above the gesture/navigation bar. The capsule is a button: tapping it opens the search
 * screen ([com.qtekfun.ultimatenotes.ui.search.SearchScreen]), which keeps its own capsule glued
 * above the keyboard.
 */
@Composable
fun FloatingSearchBar(
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val searchLabel = stringResource(R.string.search_field)
        Surface(
            onClick = onSearch,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .semantics { contentDescription = searchLabel },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FilledIconButton(onClick = onNewNote, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.note_new))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingSearchBarPreview() {
    UltimateNotesTheme { FloatingSearchBar(onNewNote = {}) }
}
