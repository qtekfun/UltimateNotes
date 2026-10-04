// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import android.content.Context
import android.content.SharedPreferences
import com.qtekfun.ultimatenotes.data.lock.SettingsLockConfigSource
import com.qtekfun.ultimatenotes.data.lock.SharedPreferencesLockStore
import com.qtekfun.ultimatenotes.domain.lock.AppLockController
import com.qtekfun.ultimatenotes.domain.lock.AppLockState
import com.qtekfun.ultimatenotes.domain.lock.LockCapabilityChecker
import com.qtekfun.ultimatenotes.domain.lock.LockConfigSource
import com.qtekfun.ultimatenotes.domain.lock.LockStore
import com.qtekfun.ultimatenotes.ui.lock.AndroidLockCapabilityChecker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/** Bindings of the app lock (T13). `AppLockState` is what the widget (T15) injects. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LockModule {
    @Binds abstract fun lockStore(impl: SharedPreferencesLockStore): LockStore

    @Binds abstract fun configSource(impl: SettingsLockConfigSource): LockConfigSource

    @Binds abstract fun capability(impl: AndroidLockCapabilityChecker): LockCapabilityChecker

    @Binds abstract fun lockState(impl: AppLockController): AppLockState

    companion object {
        @Provides
        @Named(SharedPreferencesLockStore.LOCK_PREFERENCES)
        fun lockPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(
                SharedPreferencesLockStore.LOCK_PREFERENCES,
                Context.MODE_PRIVATE
            )
    }
}
