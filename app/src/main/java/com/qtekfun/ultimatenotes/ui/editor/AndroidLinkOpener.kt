// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.link.LinkOpener

/**
 * Opens links with `ACTION_VIEW` and tells the user, with a toast, when no app can. The URI was
 * already vetted by `LinkTarget.openable`. Nothing about the link is logged or shown.
 */
class AndroidLinkOpener(private val context: Context) : LinkOpener {
    override fun open(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.link_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}
