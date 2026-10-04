// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.qtekfun.ultimatenotes.sync.work.SyncScheduler
import com.qtekfun.ultimatenotes.ui.app.AppRoot
import com.qtekfun.ultimatenotes.ui.lock.LockGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var scheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LockGate { AppRoot() } }
    }

    /** Sync on app open (SPEC §5); queued work coalesces, so coming back often is cheap. */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { scheduler.enqueueNow() }
    }
}
