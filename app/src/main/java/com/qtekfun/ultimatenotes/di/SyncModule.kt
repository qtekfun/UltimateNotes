// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import android.content.Context
import androidx.work.WorkManager
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.domain.sync.SyncTrigger
import com.qtekfun.ultimatenotes.sync.queue.BackoffPolicy
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpointStore
import com.qtekfun.ultimatenotes.sync.store.SharedPreferencesCheckpointStore
import com.qtekfun.ultimatenotes.sync.store.SharedPreferencesSyncStatusStore
import com.qtekfun.ultimatenotes.sync.work.SyncEngineProvider
import com.qtekfun.ultimatenotes.sync.work.SyncRunner
import com.qtekfun.ultimatenotes.sync.work.SyncScheduler
import com.qtekfun.ultimatenotes.sync.work.SyncSource
import com.qtekfun.ultimatenotes.sync.work.SyncStatusStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Wiring of the sync engine, its stores and the WorkManager workers (T08). */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    private const val CHECKPOINT_PREFERENCES = "sync_checkpoint"
    private const val STATUS_PREFERENCES = "sync_status"

    @Provides
    fun noteSyncWriteDao(database: UltimateNotesDatabase): NoteSyncWriteDao =
        database.noteSyncWriteDao()

    @Provides
    @Singleton
    fun checkpointStore(@ApplicationContext context: Context): SyncCheckpointStore =
        SharedPreferencesCheckpointStore(
            context.getSharedPreferences(CHECKPOINT_PREFERENCES, Context.MODE_PRIVATE)
        )

    @Provides
    @Singleton
    fun statusStore(@ApplicationContext context: Context): SyncStatusStore =
        SharedPreferencesSyncStatusStore(
            context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
        )

    @Provides
    fun backoffPolicy(): BackoffPolicy = BackoffPolicy()

    @Provides
    fun syncSource(provider: SyncEngineProvider): SyncSource = provider

    @Provides
    fun syncRunner(
        source: SyncSource,
        status: SyncStatusStore,
        clock: Clock,
        backoff: BackoffPolicy
    ): SyncRunner = SyncRunner(source, status, clock, backoff)

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /** The real trigger the list's pull-to-refresh uses (replaces the T10 no-op). */
    @Provides
    fun syncTrigger(scheduler: SyncScheduler): SyncTrigger = scheduler
}
