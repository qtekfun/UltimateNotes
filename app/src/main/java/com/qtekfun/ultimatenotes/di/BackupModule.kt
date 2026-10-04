// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.di

import com.qtekfun.ultimatenotes.data.backup.PassphraseBackupCipher
import com.qtekfun.ultimatenotes.domain.backup.BackupCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    fun backupCipher(): BackupCipher = PassphraseBackupCipher()
}
