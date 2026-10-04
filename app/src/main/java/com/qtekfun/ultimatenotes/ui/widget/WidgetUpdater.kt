// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.qtekfun.ultimatenotes.domain.widget.WidgetChanges
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Redraws the widget whenever [WidgetChanges] says it may be stale. Started with the app. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val changes: WidgetChanges
) {
    fun start(scope: CoroutineScope) {
        changes.observe().onEach { NotesWidget().updateAll(context) }.launchIn(scope)
    }
}
