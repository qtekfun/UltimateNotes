// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val KEY_THEME = "theme"
private const val KEY_AMOLED = "amoled"
private const val KEY_DYNAMIC_COLOR = "dynamic_color"
private const val KEY_SORT_ORDER = "sort_order"
private const val KEY_SYNC_INTERVAL = "sync_interval"
private const val KEY_SYNC_NETWORK = "sync_network"

/**
 * Per-device preferences. They are not note data, so they live in SharedPreferences rather than
 * in Room, and need no extra library.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @Named(SETTINGS_PREFERENCES) private val preferences: SharedPreferences
) {
    /** The current settings, and every change after. */
    val settings: Flow<AppSettings> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun setTheme(theme: ThemeMode) = preferences.edit { putString(KEY_THEME, theme.name) }

    fun setAmoled(on: Boolean) = preferences.edit { putBoolean(KEY_AMOLED, on) }

    fun setDynamicColor(on: Boolean) = preferences.edit { putBoolean(KEY_DYNAMIC_COLOR, on) }

    fun setSortOrder(order: NoteSortOrder) =
        preferences.edit { putString(KEY_SORT_ORDER, order.name) }

    fun setSyncInterval(interval: SyncInterval) =
        preferences.edit { putString(KEY_SYNC_INTERVAL, interval.name) }

    fun setSyncNetwork(network: SyncNetwork) =
        preferences.edit { putString(KEY_SYNC_NETWORK, network.name) }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        val theme = preferences.getString(KEY_THEME, null)
        val sortOrder = preferences.getString(KEY_SORT_ORDER, null)
        val interval = preferences.getString(KEY_SYNC_INTERVAL, null)
        val network = preferences.getString(KEY_SYNC_NETWORK, null)
        return AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: defaults.theme,
            amoled = preferences.getBoolean(KEY_AMOLED, defaults.amoled),
            dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
            sortOrder = NoteSortOrder.entries.firstOrNull { it.name == sortOrder }
                ?: defaults.sortOrder,
            syncInterval = SyncInterval.entries.firstOrNull { it.name == interval }
                ?: defaults.syncInterval,
            syncNetwork = SyncNetwork.entries.firstOrNull { it.name == network }
                ?: defaults.syncNetwork
        )
    }

    companion object {
        const val SETTINGS_PREFERENCES = "settings"
    }
}
