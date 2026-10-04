// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.widget.LaunchRequest
import com.qtekfun.ultimatenotes.domain.widget.LoadWidgetContent
import com.qtekfun.ultimatenotes.domain.widget.WidgetContent
import com.qtekfun.ultimatenotes.domain.widget.WidgetNote
import com.qtekfun.ultimatenotes.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotesWidgetEntryPoint {
    fun loadWidgetContent(): LoadWidgetContent
}

/**
 * The home-screen widget (SPEC §8). Small (2x2): the latest note and a new-note button; larger: the
 * list of recent notes. The decisions are in `domain.widget`; this only draws them. Content is
 * loaded once per update; updates are requested by [WidgetUpdater].
 */
class NotesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LIST))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotesWidgetEntryPoint::class.java
        )
        val content = entry.loadWidgetContent()()
        provideContent { GlanceTheme { WidgetBody(content) } }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val LIST = DpSize(220.dp, 180.dp)
    }
}

internal const val EXTRA_NEW_NOTE = "com.qtekfun.ultimatenotes.extra.NEW_NOTE"
internal const val EXTRA_NOTE_ID = "com.qtekfun.ultimatenotes.extra.NOTE_ID"

private val MIN_TOUCH = 48.dp

/** Opens the app; for a request, the extras say what to open and the data keeps intents distinct. */
@Composable
private fun openApp(request: LaunchRequest? = null): Action {
    val context = LocalContext.current
    val intent = Intent(context, MainActivity::class.java).apply {
        when (request) {
            is LaunchRequest.OpenNote -> {
                putExtra(EXTRA_NOTE_ID, request.localId)
                data = "ultimatenotes://note/${request.localId}".toUri()
            }

            LaunchRequest.NewNote -> {
                putExtra(EXTRA_NEW_NOTE, true)
                data = "ultimatenotes://new".toUri()
            }

            null -> Unit
        }
    }
    return actionStartActivity(intent)
}

@Composable
private fun WidgetBody(content: WidgetContent) {
    val size = LocalSize.current
    val large = size.width >= NotesWidget.LIST.width && size.height >= NotesWidget.LIST.height
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp)
    ) {
        when (content) {
            WidgetContent.SignedOut -> Message(R.string.widget_signed_out, R.drawable.ic_notes)

            WidgetContent.Locked -> Message(R.string.widget_locked, R.drawable.ic_widget_lock)

            WidgetContent.Empty -> Message(
                R.string.widget_empty,
                R.drawable.ic_notes,
                newNote = true
            )

            is WidgetContent.Notes -> if (large) {
                NoteList(content.notes)
            } else {
                LatestNote(content.notes.first())
            }
        }
    }
}

/** A state with no notes to show: tapping anywhere opens the app. */
@Composable
private fun Message(text: Int, icon: Int, newNote: Boolean = false) {
    val context = LocalContext.current
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(openApp()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
            Spacer(GlanceModifier.size(8.dp))
            Text(
                text = context.getString(text),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)
            )
        }
        if (newNote) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                NewNoteButton()
            }
        }
    }
}

@Composable
private fun NewNoteButton() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.size(MIN_TOUCH)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(MIN_TOUCH / 2)
            .clickable(openApp(LaunchRequest.NewNote))
            .semantics { contentDescription = context.getString(R.string.widget_new_note) },
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary)
        )
    }
}

/** 2x2: the newest note fills the widget, the new-note button sits in the corner. */
@Composable
private fun LatestNote(note: WidgetNote) {
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(bottom = MIN_TOUCH + 4.dp)) {
            NoteRow(note, maxTitleLines = 2)
        }
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            NewNoteButton()
        }
    }
}

/** Larger: a header with the new-note button above the recent notes. */
@Composable
private fun NoteList(notes: List<WidgetNote>) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.widget_label),
                modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            NewNoteButton()
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(notes, itemId = { it.localId }) { NoteRow(it, maxTitleLines = 1) }
        }
    }
}

@Composable
private fun NoteRow(note: WidgetNote, maxTitleLines: Int) {
    val context = LocalContext.current
    val title = note.title.ifEmpty { context.getString(R.string.widget_untitled) }
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clickable(openApp(LaunchRequest.OpenNote(note.localId)))
            .semantics { contentDescription = context.getString(R.string.widget_open_note, title) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (note.favorite) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_star),
                contentDescription = context.getString(R.string.widget_favorite),
                modifier = GlanceModifier.size(14.dp),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.primary)
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Column {
            Text(
                text = title,
                maxLines = maxTitleLines,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            if (note.preview.isNotEmpty()) {
                Text(
                    text = note.preview,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
                )
            }
        }
    }
}
