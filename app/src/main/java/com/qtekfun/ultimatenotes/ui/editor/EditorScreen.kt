// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.markdown.FormatAction
import com.qtekfun.ultimatenotes.ui.export.ExportFlow
import com.qtekfun.ultimatenotes.ui.main.MoveDialog
import kotlinx.coroutines.launch

/**
 * The note editor: a title line and, below it, a single text field whose content is the Markdown
 * source, drawn with styles, with a formatting bar above the keyboard. Back (or leaving the app) saves; a note that was
 * opened as new and left blank is discarded.
 *
 * @param noteId the note's local id, or [NEW_NOTE_ID].
 * @param newNoteCategory the folder a new note is created in.
 * @param onDeleted called with the note to delete; the list owns the delete-with-undo.
 */
@Composable
fun EditorScreen(
    noteId: Long,
    newNoteCategory: String,
    onClose: () -> Unit,
    onDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(noteId, newNoteCategory) { viewModel.open(noteId, newNoteCategory) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveInBackground() }
    LaunchedEffect(state.missing) { if (state.missing) onClose() }
    var exporting by rememberSaveable { mutableStateOf(false) }
    val leave = {
        viewModel.close()
        onClose()
    }
    BackHandler(onBack = leave)
    EditorContent(
        state = state,
        text = viewModel.text,
        title = viewModel.title,
        isNew = noteId == NEW_NOTE_ID,
        actions = EditorActions(
            onBack = leave,
            onFormat = viewModel::format,
            onToggleFavorite = viewModel::toggleFavorite,
            onMove = viewModel::move,
            onExport = { exporting = true },
            onDelete = {
                scope.launch {
                    viewModel.closeForDelete()?.let(onDeleted)
                    onClose()
                }
            }
        ),
        modifier = modifier
    )
    ExportFlow(
        title = { viewModel.title.text.toString() },
        content = { viewModel.text.text.toString() },
        visible = exporting,
        onDismiss = { exporting = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun EditorContent(
    state: EditorUiState,
    text: TextFieldState,
    title: TextFieldState,
    isNew: Boolean,
    actions: EditorActions,
    modifier: Modifier = Modifier
) {
    val editable = state.loaded && !state.readOnly
    var moving by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.category) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                actions = {
                    UndoRedoButtons(text, editable)
                    OverflowMenu(state, actions, onMove = { moving = true })
                }
            )
        },
        bottomBar = { if (editable) FormattingBar(actions.onFormat) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.readOnly) {
                Text(
                    stringResource(R.string.editor_readonly),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TEXT_PADDING, vertical = 8.dp)
                )
            }
            if (state.loaded) {
                NoteTitleField(title, editable)
                NoteTextField(text, editable, requestFocus = isNew)
            }
        }
    }
    if (moving) {
        MoveDialog(
            folders = state.folders,
            onDismiss = { moving = false },
            onMove = {
                moving = false
                actions.onMove(it)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UndoRedoButtons(text: TextFieldState, enabled: Boolean) {
    val undo = stringResource(R.string.undo)
    val redo = stringResource(R.string.editor_redo)
    IconButton(
        onClick = { text.undoState.undo() },
        enabled = enabled && text.undoState.canUndo,
        modifier = Modifier.semantics { contentDescription = undo }
    ) { Text("↶") }
    IconButton(
        onClick = { text.undoState.redo() },
        enabled = enabled && text.undoState.canRedo,
        modifier = Modifier.semantics { contentDescription = redo }
    ) { Text("↷") }
}

@Composable
private fun OverflowMenu(state: EditorUiState, actions: EditorActions, onMove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val enabled = state.loaded && !state.readOnly
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.editor_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (state.favorite) R.string.note_unfavorite else R.string.note_favorite
                    )
                )
            },
            enabled = enabled,
            onClick = {
                open = false
                actions.onToggleFavorite()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.select_move)) },
            enabled = enabled,
            onClick = {
                open = false
                onMove()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export_note)) },
            enabled = state.loaded,
            onClick = {
                open = false
                actions.onExport()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.note_delete)) },
            enabled = enabled,
            onClick = {
                open = false
                actions.onDelete()
            }
        )
    }
}

/** The note's title: one line above the body; Enter moves on to the body. */
@Composable
private fun NoteTitleField(title: TextFieldState, editable: Boolean) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val fieldDescription = stringResource(R.string.editor_title_field)
    val hint = stringResource(R.string.editor_title_hint)
    BasicTextField(
        state = title,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .semantics { contentDescription = fieldDescription }
            .padding(start = TEXT_PADDING, end = TEXT_PADDING, top = TEXT_PADDING),
        readOnly = !editable,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onSurface),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next
        ),
        onKeyboardAction = { focusManager.moveFocus(FocusDirection.Down) },
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(colors.primary),
        decorator = { inner ->
            Box {
                if (title.text.isEmpty()) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurfaceVariant.copy(alpha = HINT_ALPHA)
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun NoteTextField(text: TextFieldState, editable: Boolean, requestFocus: Boolean) {
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors) {
        EditorPalette(
            marker = colors.onSurfaceVariant.copy(alpha = MARKER_ALPHA),
            link = colors.primary,
            quote = colors.onSurfaceVariant,
            codeBackground = colors.surfaceVariant.copy(alpha = CODE_ALPHA),
            accent = colors.primary,
            done = colors.onSurface.copy(alpha = DONE_ALPHA)
        )
    }
    val source = remember { StyleRunSource() }
    val styler = remember(source, palette) { MarkdownStyler(source, palette) }
    val scroll = rememberScrollState()
    val holder = remember { LayoutHolder() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { source.track(text) }
    LaunchedEffect(requestFocus) { if (requestFocus) focus.requestFocus() }
    val toggleDescription = stringResource(R.string.editor_toggle_checkbox)
    val fieldDescription = stringResource(R.string.editor_text_field)
    BasicTextField(
        state = text,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .semantics {
                contentDescription = fieldDescription
                customActions = listOf(
                    CustomAccessibilityAction(toggleDescription) { text.toggleChecklistAtCaret() }
                )
            }
            .padding(TEXT_PADDING)
            .toggleCheckboxOnTap(
                state = text,
                source = source,
                layout = { holder.getResult?.invoke() },
                scrollOffset = { scroll.value },
                enabled = editable
            ),
        readOnly = !editable,
        inputTransformation = ContinueListOnEnter,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(),
        onTextLayout = { getResult -> holder.getResult = getResult },
        cursorBrush = SolidColor(colors.primary),
        outputTransformation = styler,
        scrollState = scroll
    )
}

/** The field's latest text layout, handed over by `onTextLayout` (not state: written during layout). */
private class LayoutHolder {
    var getResult: (() -> TextLayoutResult?)? = null
}

private const val MARKER_ALPHA = 0.6f
private const val CODE_ALPHA = 0.6f
private const val DONE_ALPHA = 0.55f
private const val HINT_ALPHA = 0.7f
private val MIN_TOUCH_TARGET = 48.dp
internal val TEXT_PADDING = 16.dp

/** What the editor screen asks for. */
class EditorActions(
    val onBack: () -> Unit = {},
    val onFormat: (FormatAction) -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onMove: (String) -> Unit = {},
    val onExport: () -> Unit = {},
    val onDelete: () -> Unit = {}
)
