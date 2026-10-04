// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.qtekfun.ultimatenotes.domain.widget.LaunchRequest
import com.qtekfun.ultimatenotes.sync.work.SyncScheduler
import com.qtekfun.ultimatenotes.ui.app.AppRoot
import com.qtekfun.ultimatenotes.ui.lock.LockGate
import com.qtekfun.ultimatenotes.ui.widget.EXTRA_NEW_NOTE
import com.qtekfun.ultimatenotes.ui.widget.EXTRA_NOTE_ID
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var scheduler: SyncScheduler

    /** What the widget asked for, until the app root has acted on it. */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreation (rotation) gets the same intent again; only a fresh launch is a request.
        if (savedInstanceState == null) launchRequest = requestOf(intent)
        setContent {
            LockGate {
                AppRoot(
                    launchRequest = launchRequest,
                    onLaunchHandled = { launchRequest = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestOf(intent)?.let { launchRequest = it }
    }

    private fun requestOf(intent: Intent): LaunchRequest? = LaunchRequest.from(
        newNote = intent.getBooleanExtra(EXTRA_NEW_NOTE, false),
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
    )

    /** Sync on app open (SPEC §5); queued work coalesces, so coming back often is cheap. */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { scheduler.enqueueNow() }
    }
}
