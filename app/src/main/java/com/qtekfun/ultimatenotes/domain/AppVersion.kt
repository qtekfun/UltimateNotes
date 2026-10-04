// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain

/** An app version: MAJOR.MINOR.PATCH, optionally followed by -rc.N (the grammar of `appVersion`). */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val releaseCandidate: Int?) {
    val isPreRelease: Boolean get() = releaseCandidate != null

    override fun toString(): String {
        val suffix = if (releaseCandidate == null) "" else "-rc.$releaseCandidate"
        return "$major.$minor.$patch$suffix"
    }

    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""")

        /** Parses [text], or returns null when it is not a valid version. */
        fun parse(text: String): AppVersion? {
            val match = PATTERN.matchEntire(text) ?: return null
            val groups = match.groupValues
            return AppVersion(
                major = groups[1].toInt(),
                minor = groups[2].toInt(),
                patch = groups[3].toInt(),
                releaseCandidate = groups[4].toIntOrNull()
            )
        }
    }
}
