// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

/** One note row of the widget: only what it shows. Never built while the app is locked. */
data class WidgetNote(
    val localId: Long,
    /** The stored title; blank when the note has none (the UI shows a localized fallback). */
    val title: String,
    /** One line of body text, already truncated. */
    val preview: String,
    val favorite: Boolean
) {
    /** Never prints the title or preview of the note. */
    override fun toString(): String = "WidgetNote(localId=$localId, content=<redacted>)"
}

/** What the widget shows. [Locked] and [SignedOut] carry no note data by construction. */
sealed interface WidgetContent {
    data object SignedOut : WidgetContent

    data object Locked : WidgetContent

    data object Empty : WidgetContent

    data class Notes(val notes: List<WidgetNote>) : WidgetContent
}
