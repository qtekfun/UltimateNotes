// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.backup

import com.qtekfun.ultimatenotes.data.settings.SyncInterval
import com.qtekfun.ultimatenotes.data.settings.SyncNetwork
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.domain.lock.LockTimeout
import kotlinx.serialization.Serializable

/**
 * What a backup carries: the settings and the account. Notes are not included, they live on the
 * server and are synced again after a restore.
 */
@Serializable
data class BackupPayload(val settings: BackupSettings, val account: BackupAccount)

/**
 * The settings that make sense on another device. Whether the app lock is on is left out on
 * purpose: restoring it on a phone without a screen lock would lock the user out.
 */
@Serializable
data class BackupSettings(
    val theme: ThemeMode,
    val amoled: Boolean,
    val dynamicColor: Boolean,
    val sortOrder: NoteSortOrder,
    val syncInterval: SyncInterval,
    val syncNetwork: SyncNetwork,
    val lockTimeout: LockTimeout,
    val secureWindow: Boolean
)

/** The account with its app password. The `toString` never reveals the password. */
@Serializable
data class BackupAccount(val serverUrl: String, val username: String, val appPassword: String) {
    override fun toString(): String =
        "BackupAccount(serverUrl=$serverUrl, username=$username, appPassword=***)"
}
