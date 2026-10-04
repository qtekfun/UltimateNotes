// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes

import android.app.Application
import androidx.work.Configuration
import com.qtekfun.ultimatenotes.sync.work.SyncScheduler
import com.qtekfun.ultimatenotes.sync.work.SyncWorkerFactory
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class UltimateNotesApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: SyncWorkerFactory

    @Inject lateinit var scheduler: Lazy<SyncScheduler>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        scheduler.get().keepPeriodicInSync(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
