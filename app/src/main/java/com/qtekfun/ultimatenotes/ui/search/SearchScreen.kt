// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.search.Highlighted
import com.qtekfun.ultimatenotes.domain.search.SearchResult
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val BAR_HEIGHT = 56.dp
private val ROW_MIN_HEIGHT = 72.dp
private val ICON_SIZE = 16.dp

/**
 * The search screen (SPEC §7): results above, and the search capsule pinned at the bottom. The
 * content is padded by the safe-drawing insets, which include the keyboard, so the capsule
 * rides on top of it.
 */
@Composable
fun SearchScreen(state: SearchUiState, actions: SearchActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.onClose)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScopeChips(state, actions.onScopeChange)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.status) {
                    SearchStatus.IDLE -> Message(R.string.search_hint, null)

                    SearchStatus.NO_RESULTS ->
                        Message(R.string.search_no_results, R.string.search_no_results_hint)

                    SearchStatus.SEARCHING, SearchStatus.RESULTS ->
                        Results(state.results, state.scopeFolder == null, actions.onOpenNote)
                }
            }
            SearchField(state.query, actions)
        }
    }
}

@Composable
private fun ScopeChips(state: SearchUiState, onScope: (SearchScope) -> Unit) {
    val folder = state.scopeFolder ?: return
    val label = when (folder) {
        is FolderSelection.Folder -> folder.name
        else -> stringResource(R.string.folder_none)
    }
    val groupLabel = stringResource(R.string.search_scope_label)
    Row(
        modifier = Modifier
            .selectableGroup()
            .semantics { contentDescription = groupLabel }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = state.scope == SearchScope.ALL,
            onClick = { onScope(SearchScope.ALL) },
            label = { Text(stringResource(R.string.folder_all)) }
        )
        FilterChip(
            selected = state.scope == SearchScope.FOLDER,
            onClick = { onScope(SearchScope.FOLDER) },
            label = { Text(stringResource(R.string.search_scope_folder, label)) }
        )
    }
}

@Composable
private fun Message(title: Int, hint: Int?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
                textAlign = TextAlign.Center
            )
            if (hint != null) {
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Results(results: List<SearchResult>, showFolder: Boolean, onOpenNote: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.localId }) { result ->
            ResultRow(result, showFolder, onOpenNote)
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, showFolder: Boolean, onOpen: (Long) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(result.localId) }
            .heightIn(min = ROW_MIN_HEIGHT)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (result.title.text.isEmpty()) {
            Text(
                stringResource(R.string.note_untitled),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                rememberHighlightedText(result.title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (result.snippet.text.isEmpty()) {
                AnnotatedString(stringResource(R.string.note_no_preview))
            } else {
                rememberHighlightedText(result.snippet)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        ResultMeta(result, showFolder)
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

/** Date and, when the results come from several folders, the folder of the note. */
@Composable
private fun ResultMeta(result: SearchResult, showFolder: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatDate(result.modified),
            style = MaterialTheme.typography.labelMedium,
            color = muted
        )
        if (showFolder && result.category.isNotEmpty()) {
            Icon(
                painterResource(R.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = muted
            )
            Text(
                result.category,
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun formatDate(modified: Instant): String {
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    return remember(modified, locale, zone) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).withZone(zone)
            .format(modified)
    }
}

/** The capsule: search field with a clear button, and the button that closes the search. */
@Composable
private fun SearchField(query: String, actions: SearchActions) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Coming back from a note keeps the query, and the keyboard stays down.
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = actions.onQueryChange,
            modifier = Modifier.weight(1f).heightIn(min = BAR_HEIGHT).focusRequester(focus),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_field)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { actions.onQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.search_clear)
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        FilledTonalIconButton(onClick = actions.onClose, modifier = Modifier.size(BAR_HEIGHT)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_close))
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("MagicNumber")
private fun SearchScreenPreview() {
    UltimateNotesTheme {
        SearchScreen(
            state = SearchUiState(
                active = true,
                query = "café",
                scopeFolder = FolderSelection.Folder("Work"),
                status = SearchStatus.RESULTS,
                results = listOf(
                    SearchResult(
                        localId = 1,
                        title = Highlighted("Café menu", listOf(TextRange(0, 4))),
                        snippet = Highlighted("…the café on the corner…", listOf(TextRange(5, 9))),
                        category = "Work",
                        favorite = false,
                        modified = Instant.parse("2026-10-01T10:00:00Z")
                    )
                )
            ),
            actions = SearchActions()
        )
    }
}
