// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes

import android.app.Application
import androidx.work.Configuration
import com.qtekfun.ultimatenotes.domain.export.ExportDirectory
import com.qtekfun.ultimatenotes.sync.work.SyncScheduler
import com.qtekfun.ultimatenotes.sync.work.SyncWorkerFactory
import com.qtekfun.ultimatenotes.ui.widget.WidgetUpdater
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class UltimateNotesApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: SyncWorkerFactory

    @Inject lateinit var scheduler: Lazy<SyncScheduler>

    @Inject lateinit var widgetUpdater: Lazy<WidgetUpdater>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scheduler.get().keepPeriodicInSync(scope)
        scope.launch(Dispatchers.IO) {
            ExportDirectory(File(cacheDir, ExportDirectory.NAME))
                .deleteOlderThan(Clock.systemUTC().millis() - ExportDirectory.MAX_AGE_MS)
        }
        widgetUpdater.get().start(scope)
    }
}
