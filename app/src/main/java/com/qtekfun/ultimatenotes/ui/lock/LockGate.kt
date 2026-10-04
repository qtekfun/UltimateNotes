// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withResumed
import com.qtekfun.ultimatenotes.ui.app.AppViewModel
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import kotlinx.coroutines.launch

/**
 * Wraps the whole app. While the app is locked the content stays composed (so no state is lost)
 * but is invisible, unreachable by touch, focus and accessibility services, and a lock screen
 * covers it until the user authenticates. Also keeps `FLAG_SECURE` on the window when needed.
 */
@Composable
fun LockGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val viewModel: LockViewModel = viewModel()
    val activity = LocalActivity.current as? FragmentActivity
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val window by viewModel.window.collectAsStateWithLifecycle()
    val secureNow by rememberUpdatedState(window.secureWindow || locked)
    val lockEnabled by rememberUpdatedState(window.lockEnabled)
    val theme by viewModel<AppViewModel>().theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // A rotation or theme change recreates the activity; it is not leaving the app.
        if (activity?.isChangingConfigurations != true) viewModel.onBackground()
    }
    // The recents thumbnail is taken right after pausing: hide it before that when a lock is on.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (lockEnabled) activity?.window?.setSecure(true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { activity?.window?.setSecure(secureNow) }
    LaunchedEffect(window.secureWindow, locked) { activity?.window?.setSecure(secureNow) }

    val authenticate: () -> Unit = {
        if (activity != null) {
            scope.launch { viewModel.authenticate(AndroidLockPrompt(activity)) }
        }
    }
    LaunchedEffect(locked) {
        if (locked) {
            focus.clearFocus(force = true)
            focus.moveFocus(FocusDirection.Exit)
            // Prompt on its own once the screen is in front of the user.
            lifecycle.withResumed { }
            authenticate()
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            if (locked) {
                Modifier.fillMaxSize().alpha(0f).clearAndSetSemantics { }
            } else {
                Modifier.fillMaxSize()
            }
        ) { content() }
        if (locked) UltimateNotesTheme(theme) { LockScreen(onUnlock = authenticate) }
    }
}

private fun Window.setSecure(on: Boolean) {
    if (on) {
        addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
