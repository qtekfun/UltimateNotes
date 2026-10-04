// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.lock

import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.lock.LockConfig
import com.qtekfun.ultimatenotes.domain.lock.LockConfigSource
import javax.inject.Inject

/** Feeds the lock controller from the device settings. */
class SettingsLockConfigSource @Inject constructor(private val repository: SettingsRepository) :
    LockConfigSource {
    override fun current(): LockConfig = repository.current.toLockConfig()

    override fun disable() = repository.setAppLockEnabled(false)
}

fun AppSettings.toLockConfig() = LockConfig(enabled = appLockEnabled, timeout = lockTimeout)
