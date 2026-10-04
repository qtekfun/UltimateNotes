// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import java.util.Locale

/**
 * Decides whether the destination of a link written in a note may be handed to the system.
 * Destinations come from untrusted text (a shared note can say anything), so this is an
 * allow-list: anything not clearly a web, mail or phone link is refused. See ADR 0013.
 *
 * It never logs or reports the destination.
 */
object LinkTarget {
    /** Longer destinations are refused: they cannot be a sane link and an Intent has a size limit. */
    const val MAX_LENGTH = 8192

    private val SCHEMES = setOf("https", "http", "mailto", "tel")
    private val WEB_SCHEMES = setOf("https", "http")
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)

    /**
     * Returns [destination] normalized (surrounding whitespace removed, scheme in lower case) if it
     * is an `https`, `http`, `mailto` or `tel` link; otherwise null.
     *
     * Refused: an empty destination, more than [MAX_LENGTH] characters, any whitespace inside,
     * control or invisible formatting characters (`java\nscript:`, bidi overrides), a missing
     * scheme (relative paths, `//host`, `#anchor`, `www.example.org`, a percent-encoded colon),
     * every other scheme, `http(s)` without a host or with `user@` credentials, and `mailto:` /
     * `tel:` with nothing after the colon.
     */
    fun openable(destination: String): String? {
        val trimmed = destination.trim()
        val match = if (trimmed.length > MAX_LENGTH || trimmed.any(::isForbidden)) {
            null
        } else {
            SCHEME.matchEntire(trimmed)
        }
        return match?.let {
            normalized(it.groupValues[1].lowercase(Locale.ROOT), it.groupValues[2])
        }
    }

    private fun normalized(scheme: String, rest: String): String? {
        val allowed = scheme in SCHEMES && when (scheme) {
            in WEB_SCHEMES -> hasPlainHost(rest)
            else -> rest.isNotEmpty()
        }
        return if (allowed) "$scheme:$rest" else null
    }

    private fun isForbidden(c: Char): Boolean =
        c.isWhitespace() || c.isISOControl() || c.category == CharCategory.FORMAT

    /** `//host[:port][/...]` with a non-empty host and no credentials. */
    private fun hasPlainHost(rest: String): Boolean {
        if (!rest.startsWith("//")) return false
        val authority = rest.substring(2).takeWhile { it != '/' && it != '?' && it != '#' }
        return '@' !in authority && !authority.startsWith(':') && authority.isNotEmpty()
    }
}
