// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.ui.login.LoginScreen
import com.qtekfun.ultimatenotes.ui.main.MainScreen
import com.qtekfun.ultimatenotes.ui.settings.SettingsScreen
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme

/** Themed app root: startup routing between login, the main screen and settings. */
@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    var inSettings by rememberSaveable(destination) { mutableStateOf(false) }
    UltimateNotesTheme(theme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (destination) {
                // The session is read in a blink; a blank themed screen avoids a login flash.
                AppDestination.LOADING -> Box(Modifier.fillMaxSize())

                AppDestination.LOGIN -> LoginScreen()

                AppDestination.MAIN -> if (inSettings) {
                    BackHandler { inSettings = false }
                    SettingsScreen(onBack = { inSettings = false })
                } else {
                    MainScreen(onOpenSettings = { inSettings = true })
                }
            }
        }
    }
}
