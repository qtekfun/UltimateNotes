// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.settings

import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder

/** Preferences of this device (SPEC §7, Settings). */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Pure black backgrounds in dark mode, for OLED screens. */
    val amoled: Boolean = false,
    /** Material You colors from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** How the note list is ordered (SPEC §7); modification date by default. */
    val sortOrder: NoteSortOrder = NoteSortOrder.MODIFIED,
    /** How often the periodic background sync runs. */
    val syncInterval: SyncInterval = SyncInterval.HOUR,
    /** Connections the background syncs may use. */
    val syncNetwork: SyncNetwork = SyncNetwork.ANY
)
