// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme

/** Opaque screen that covers the app while it is locked; shows nothing from the notes. */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_notes),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onUnlock, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}

@Preview
@Composable
private fun LockScreenPreview() {
    UltimateNotesTheme { LockScreen(onUnlock = {}) }
}
