// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.lock.toLockConfig
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.lock.AppLockController
import com.qtekfun.ultimatenotes.domain.lock.AuthResult
import com.qtekfun.ultimatenotes.domain.lock.LockPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the window needs to know: whether the lock is on, and the screenshot-blocking choice. */
data class LockWindowState(val lockEnabled: Boolean, val secureWindow: Boolean)

/** Connects the settings and the lifecycle to the [AppLockController]. */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val controller: AppLockController,
    settings: SettingsRepository
) : ViewModel() {
    val locked: StateFlow<Boolean> = controller.locked

    val window: StateFlow<LockWindowState> = settings.settings
        .map { LockWindowState(it.appLockEnabled, it.secureWindow) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settings.current.let { LockWindowState(it.appLockEnabled, it.secureWindow) }
        )

    init {
        viewModelScope.launch {
            settings.settings.collect { controller.onConfigChanged(it.toLockConfig()) }
        }
    }

    fun onForeground() = controller.onEnterForeground()

    fun onBackground() = controller.onEnterBackground()

    suspend fun authenticate(prompt: LockPrompt): AuthResult = controller.authenticate(prompt)
}
