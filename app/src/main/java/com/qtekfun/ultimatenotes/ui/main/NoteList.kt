// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.list.NoteGroup
import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import com.qtekfun.ultimatenotes.domain.list.NoteSection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val ROW_MIN_HEIGHT = 72.dp
private val ICON_SIZE = 16.dp

/** The Apple Notes style list: dated sections, pull to refresh, swipe and multi-select. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteList(
    state: MainUiState,
    actions: ListActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            if (state.groups.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.loaded) EmptyState(state.selection)
                    }
                }
            }
            val showFolder = state.selection == FolderSelection.All ||
                state.selection == FolderSelection.Favorites
            state.groups.forEach { group ->
                stickyHeader(key = group.section.key()) { SectionHeader(group.section) }
                items(group.notes, key = { it.localId }) { note ->
                    SwipeableNoteRow(
                        note = note,
                        display = RowDisplay(
                            section = group.section,
                            showFolder = showFolder,
                            selecting = state.selecting,
                            selected = note.localId in state.selectedIds
                        ),
                        actions = actions
                    )
                }
            }
        }
    }
}

private fun NoteSection.key(): String = when (this) {
    is NoteSection.Month -> "month-$month"
    else -> this::class.simpleName.orEmpty()
}

@Composable
private fun EmptyState(selection: FolderSelection) {
    val (title, hint) = when (selection) {
        FolderSelection.Favorites ->
            R.string.list_empty_favorites to R.string.list_empty_favorites_hint

        FolderSelection.All, FolderSelection.NoFolder ->
            R.string.main_empty to R.string.list_empty_hint

        is FolderSelection.Folder -> R.string.list_empty_folder to R.string.list_empty_hint
    }
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(section: NoteSection) {
    Text(
        text = sectionTitle(section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun sectionTitle(section: NoteSection): String = when (section) {
    NoteSection.Pinned -> stringResource(R.string.section_pinned)

    NoteSection.Today -> stringResource(R.string.section_today)

    NoteSection.Yesterday -> stringResource(R.string.section_yesterday)

    NoteSection.Previous7Days -> stringResource(R.string.section_previous_7_days)

    NoteSection.Previous30Days -> stringResource(R.string.section_previous_30_days)

    NoteSection.Alphabetical -> stringResource(R.string.section_notes)

    is NoteSection.Month -> {
        val locale = LocalLocale.current.platformLocale
        val pattern = if (section.includesYear) "LLLL yyyy" else "LLLL"
        remember(section, locale) {
            DateTimeFormatter.ofPattern(pattern, locale).format(section.month)
                .replaceFirstChar { it.titlecase(locale) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteRow(note: NoteListItem, display: RowDisplay, actions: ListActions) {
    val dismiss = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = !display.selecting,
        enableDismissFromEndToStart = !display.selecting,
        backgroundContent = { SwipeBackground(dismiss.dismissDirection, note.favorite) },
        onDismiss = { direction ->
            when (direction) {
                // Favoriting snaps the row back; deleting lets it leave (it is hidden next).
                SwipeToDismissBoxValue.StartToEnd -> {
                    actions.onToggleFavorite(note)
                    scope.launch { dismiss.reset() }
                }

                SwipeToDismissBoxValue.EndToStart -> actions.onDelete(note)

                SwipeToDismissBoxValue.Settled -> Unit
            }
        }
    ) {
        NoteRow(note, display, actions)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, favorite: Boolean) {
    val delete = direction == SwipeToDismissBoxValue.EndToStart
    val color = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = if (delete) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = when {
                    delete -> Icons.Default.Delete
                    favorite -> Icons.Default.FavoriteBorder
                    else -> Icons.Default.Favorite
                },
                contentDescription = null
            )
        }
    }
}

/** How a row is shown: where it sits and whether the list is in multi-select mode. */
private data class RowDisplay(
    val section: NoteSection,
    val showFolder: Boolean,
    val selecting: Boolean,
    val selected: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(note: NoteListItem, display: RowDisplay, actions: ListActions) {
    val favoriteLabel =
        stringResource(if (note.favorite) R.string.note_unfavorite else R.string.note_favorite)
    val deleteLabel = stringResource(R.string.note_delete)
    val background = if (display.selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    Surface(color = background) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_MIN_HEIGHT)
                    .combinedClickable(
                        onClick = {
                            if (display.selecting) {
                                actions.onToggleSelected(note.localId)
                            } else {
                                actions.onOpenNote(note.localId)
                            }
                        },
                        onLongClick = { actions.onToggleSelected(note.localId) }
                    )
                    .semantics(mergeDescendants = true) {
                        selected = display.selected
                        customActions = listOf(
                            CustomAccessibilityAction(favoriteLabel) {
                                actions.onToggleFavorite(note)
                                true
                            },
                            CustomAccessibilityAction(deleteLabel) {
                                actions.onDelete(note)
                                true
                            }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (display.selecting) Checkbox(checked = display.selected, onCheckedChange = null)
                NoteRowText(note, display, Modifier.weight(1f))
                if (note.favorite && !display.selecting) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.note_is_favorite),
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** Title, then date and a one or two line preview, then the folder when it is not obvious. */
@Composable
private fun NoteRowText(note: NoteListItem, display: RowDisplay, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = note.title.ifEmpty { stringResource(R.string.note_untitled) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = formatNoteDate(note, display.section),
                style = MaterialTheme.typography.bodyMedium,
                color = muted
            )
            Text(
                text = note.preview.ifEmpty { stringResource(R.string.note_no_preview) },
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (display.showFolder && note.category.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                    tint = muted
                )
                Text(
                    text = note.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Time for notes of today, the short date otherwise, in the device's language and zone. */
@Composable
private fun formatNoteDate(note: NoteListItem, section: NoteSection): String {
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    return remember(note.modified, section == NoteSection.Today, locale, zone) {
        val style = if (section == NoteSection.Today) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        }
        style.withLocale(locale).withZone(zone).format(note.modified)
    }
}
