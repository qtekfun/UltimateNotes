// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.ui.theme.ThemeOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app starts, and where it goes when the account changes. */
enum class AppDestination { LOADING, LOGIN, MAIN }

/**
 * Startup routing: restores the stored account, then follows the session, so signing in or
 * out moves between the login and main screens by itself. Also provides the theme.
 */
@HiltViewModel
class AppViewModel @Inject constructor(session: AccountSession, settings: SettingsRepository) :
    ViewModel() {
    private val restored = MutableStateFlow(false)

    val destination: StateFlow<AppDestination> = combine(
        restored,
        session.activeAccount
    ) { restored, account ->
        when {
            !restored -> AppDestination.LOADING
            account == null -> AppDestination.LOGIN
            else -> AppDestination.MAIN
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppDestination.LOADING)

    val theme: StateFlow<ThemeOptions> = settings.settings
        .map { ThemeOptions(it.theme, it.amoled, it.dynamicColor) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeOptions())

    init {
        viewModelScope.launch {
            session.restore()
            restored.value = true
        }
    }
}
