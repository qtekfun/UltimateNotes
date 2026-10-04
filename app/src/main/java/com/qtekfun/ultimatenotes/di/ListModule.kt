// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Bindings of the note list (T10). */
@Module
@InstallIn(SingletonComponent::class)
object ListModule {
    const val LIST_SCOPE = "listScope"
    const val LIST_DISPATCHER = "listDispatcher"

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()

    /** Outlives the screen, so a deletion still in its undo window is committed on exit. */
    @Provides
    @Singleton
    @Named(LIST_SCOPE)
    fun listScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Named(LIST_DISPATCHER)
    fun listDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
