// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.theme

import com.qtekfun.ultimatenotes.data.settings.ThemeMode

/** What the user picks in Settings; the defaults follow the system. */
data class ThemeOptions(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true
)
