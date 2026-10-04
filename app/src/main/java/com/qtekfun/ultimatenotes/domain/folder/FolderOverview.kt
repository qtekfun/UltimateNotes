// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.folder

import com.qtekfun.ultimatenotes.data.local.model.FolderCount

/** One row of the folder tree, already flattened in display order. */
data class FolderNode(val path: String, val depth: Int, val noteCount: Int) {
    val name: String get() = path.substringAfterLast('/')
}

/** Everything the folder drawer shows. Folder counts include their subfolders. */
data class FolderOverview(
    val total: Int = 0,
    val favorites: Int = 0,
    val noFolder: Int = 0,
    val folders: List<FolderNode> = emptyList()
) {
    companion object {
        /** Folds per-category counts (`a/b` nests under `a`) into the tree. */
        fun from(counts: List<FolderCount>, favorites: Int): FolderOverview {
            val perPath = sortedMapOf<List<String>, Int>(SEGMENT_ORDER)
            var noFolder = 0
            for ((category, count) in counts) {
                val segments = category.split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) noFolder += count
                for (depth in 1..segments.size) {
                    val prefix = segments.take(depth)
                    perPath[prefix] = (perPath[prefix] ?: 0) + count
                }
            }
            return FolderOverview(
                total = counts.sumOf { it.noteCount },
                favorites = favorites,
                noFolder = noFolder,
                folders = perPath.map { (segments, count) ->
                    FolderNode(segments.joinToString("/"), segments.size - 1, count)
                }
            )
        }

        /** Siblings alphabetical (case-insensitive first), each folder right before its children. */
        private val SEGMENT_ORDER = Comparator<List<String>> { a, b ->
            val common = minOf(a.size, b.size)
            for (i in 0 until common) {
                val order = a[i].compareTo(b[i], ignoreCase = true)
                if (order != 0) return@Comparator order
                val exact = a[i].compareTo(b[i])
                if (exact != 0) return@Comparator exact
            }
            a.size - b.size
        }
    }
}
