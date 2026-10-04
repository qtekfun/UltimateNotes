// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.lock

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatenotes.domain.lock.LockStore
import javax.inject.Inject
import javax.inject.Named

private const val KEY_BACKGROUNDED_AT = "backgrounded_at"

/** The single persisted lock value, in its own preferences file. It holds no user data. */
class SharedPreferencesLockStore @Inject constructor(
    @Named(LOCK_PREFERENCES) private val preferences: SharedPreferences
) : LockStore {
    override var backgroundedAt: Long?
        get() = if (preferences.contains(KEY_BACKGROUNDED_AT)) {
            preferences.getLong(KEY_BACKGROUNDED_AT, 0L)
        } else {
            null
        }
        set(value) = preferences.edit {
            if (value == null) remove(KEY_BACKGROUNDED_AT) else putLong(KEY_BACKGROUNDED_AT, value)
        }

    companion object {
        const val LOCK_PREFERENCES = "app_lock"
    }
}
