// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteListLogicTest {
    private val utc = ZoneId.of("UTC")

    /** 2026-10-15 12:00 in [zone]. */
    private fun clockAt(zone: ZoneId = utc, now: String = "2026-10-15T12:00:00"): Clock =
        Clock.fixed(LocalDateTime.parse(now).atZone(zone).toInstant(), zone)

    private fun at(local: String, zone: ZoneId = utc): Instant =
        LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun item(
        id: Long,
        modified: Instant,
        title: String = "n$id",
        category: String = "",
        favorite: Boolean = false
    ) = NoteListItem(id, title, "", category, favorite, modified)

    private fun sectionOfLocal(local: String, clock: Clock = clockAt()) =
        sectionOf(at(local, clock.zone), clock)

    // --- toListItem ---------------------------------------------------------------------------

    @Test
    fun `a stored note becomes a row with its title and a preview without the repeated line`() {
        val entity = NoteEntity(
            localId = 4,
            modified = 1_700_000_000,
            title = "Plan",
            category = "Work",
            content = "# Plan\n- first\n- second",
            favorite = true
        )
        assertEquals(
            NoteListItem(
                4,
                "Plan",
                "first second",
                "Work",
                true,
                Instant.ofEpochSecond(1_700_000_000)
            ),
            entity.toListItem()
        )
    }

    @Test
    fun `the title is the stored one even when the first line says something else`() {
        val entity = NoteEntity(localId = 1, title = "Groceries", content = "Milk\neggs")
        val item = entity.toListItem()
        assertEquals("Groceries", item.title)
        assertEquals("Milk eggs", item.preview)
    }

    @Test
    fun `a note with no body has an empty preview`() {
        assertEquals(
            "",
            NoteEntity(localId = 1, title = "From server", content = "").toListItem().preview
        )
    }

    // --- folder filtering ---------------------------------------------------------------------

    private val library = listOf(
        item(1, at("2026-10-15T08:00:00"), category = ""),
        item(2, at("2026-10-14T08:00:00"), category = "Work", favorite = true),
        item(3, at("2026-10-13T08:00:00"), category = "Work/Meetings"),
        item(4, at("2026-10-12T08:00:00"), category = "Workshop"),
        item(5, at("2026-10-11T08:00:00"), category = "Home")
    )

    private fun ids(selection: FolderSelection) =
        library.filter { it.matches(selection) }.map { it.localId }

    @Test
    fun `all notes shows everything`() =
        assertEquals(listOf(1L, 2, 3, 4, 5), ids(FolderSelection.All))

    @Test
    fun `favorites shows only favorites`() =
        assertEquals(listOf(2L), ids(FolderSelection.Favorites))

    @Test
    fun `no folder shows notes without category`() =
        assertEquals(listOf(1L), ids(FolderSelection.NoFolder))

    @Test
    fun `a folder includes its subfolders but not look-alike names`() {
        assertEquals(listOf(2L, 3), ids(FolderSelection.Folder("Work")))
        assertEquals(listOf(3L), ids(FolderSelection.Folder("Work/Meetings")))
        assertEquals(listOf(4L), ids(FolderSelection.Folder("Workshop")))
    }

    // --- sections: boundaries -----------------------------------------------------------------

    @Test
    fun `today starts at local midnight`() {
        assertEquals(NoteSection.Today, sectionOfLocal("2026-10-15T00:00:00"))
        assertEquals(NoteSection.Yesterday, sectionOfLocal("2026-10-14T23:59:59"))
    }

    @Test
    fun `a note dated in the future counts as today`() {
        assertEquals(NoteSection.Today, sectionOfLocal("2026-10-16T09:00:00"))
    }

    @Test
    fun `yesterday spans one whole calendar day`() {
        assertEquals(NoteSection.Yesterday, sectionOfLocal("2026-10-14T00:00:00"))
        assertEquals(NoteSection.Previous7Days, sectionOfLocal("2026-10-13T23:59:59"))
    }

    @Test
    fun `previous 7 days reaches back to the same weekday of last week`() {
        assertEquals(NoteSection.Previous7Days, sectionOfLocal("2026-10-08T00:00:00"))
        assertEquals(NoteSection.Previous30Days, sectionOfLocal("2026-10-07T23:59:59"))
    }

    @Test
    fun `previous 30 days reaches back 30 calendar days`() {
        assertEquals(NoteSection.Previous30Days, sectionOfLocal("2026-09-15T00:00:00"))
        assertEquals(
            NoteSection.Month(YearMonth.of(2026, 9), includesYear = false),
            sectionOfLocal("2026-09-14T23:59:59")
        )
    }

    @Test
    fun `older notes go by month and only other years show the year`() {
        assertEquals(
            NoteSection.Month(YearMonth.of(2026, 2), includesYear = false),
            sectionOfLocal("2026-02-28T23:59:59")
        )
        assertEquals(
            NoteSection.Month(YearMonth.of(2025, 12), includesYear = true),
            sectionOfLocal("2025-12-31T23:59:59")
        )
        assertEquals(
            NoteSection.Month(YearMonth.of(2025, 12), includesYear = true),
            sectionOfLocal("2025-12-01T00:00:00")
        )
    }

    @Test
    fun `month boundaries fall on local midnight`() {
        val clock = clockAt(now = "2026-12-20T12:00:00")
        assertEquals(
            NoteSection.Month(YearMonth.of(2026, 10), includesYear = false),
            sectionOfLocal("2026-10-31T23:59:59", clock)
        )
        assertEquals(
            NoteSection.Month(YearMonth.of(2026, 11), includesYear = false),
            sectionOfLocal("2026-11-01T00:00:00", clock)
        )
    }

    @Test
    fun `the 30 day window crosses a leap day and a year change`() {
        val leap = clockAt(now = "2028-03-01T10:00:00")
        assertEquals(NoteSection.Yesterday, sectionOfLocal("2028-02-29T10:00:00", leap))
        assertEquals(NoteSection.Previous30Days, sectionOfLocal("2028-01-31T00:00:00", leap))
        val newYear = clockAt(now = "2027-01-10T10:00:00")
        assertEquals(NoteSection.Previous30Days, sectionOfLocal("2026-12-11T00:00:00", newYear))
        assertEquals(
            NoteSection.Month(YearMonth.of(2026, 12), includesYear = true),
            sectionOfLocal("2026-12-10T23:59:59", newYear)
        )
    }

    // --- sections: time zones -----------------------------------------------------------------

    @Test
    fun `the same instant is today or yesterday depending on the zone`() {
        val instant = Instant.parse("2026-10-15T03:00:00Z")
        val now = Instant.parse("2026-10-15T12:00:00Z")
        val tokyo = ZoneId.of("Asia/Tokyo") // now 21:00 the 15th, note 12:00 the 15th
        val losAngeles = ZoneId.of("America/Los_Angeles") // now 05:00 the 15th, note 20:00 the 14th
        assertEquals(NoteSection.Today, sectionOf(instant, Clock.fixed(now, tokyo)))
        assertEquals(NoteSection.Yesterday, sectionOf(instant, Clock.fixed(now, losAngeles)))
    }

    @Test
    fun `far-east and far-west zones judge midnight locally`() {
        val kiritimati = ZoneId.of("Pacific/Kiritimati") // UTC+14
        val clock = clockAt(kiritimati)
        assertEquals(NoteSection.Today, sectionOfLocal("2026-10-15T00:00:00", clock))
        assertEquals(NoteSection.Yesterday, sectionOfLocal("2026-10-14T23:59:00", clock))
        val pago = ZoneId.of("Pacific/Pago_Pago") // UTC-11
        assertEquals(NoteSection.Today, sectionOfLocal("2026-10-15T00:00:00", clockAt(pago)))
    }

    @Test
    fun `a daylight saving change does not shift the day groups`() {
        val berlin = ZoneId.of("Europe/Berlin") // clocks go back on 2026-10-25
        val clock = clockAt(berlin, "2026-10-26T00:30:00")
        assertEquals(NoteSection.Yesterday, sectionOfLocal("2026-10-25T00:00:00", clock))
        assertEquals(NoteSection.Yesterday, sectionOfLocal("2026-10-25T23:59:59", clock))
        assertEquals(NoteSection.Previous7Days, sectionOfLocal("2026-10-24T23:59:59", clock))
    }

    @Test
    fun `a month boundary in the clock zone differs from UTC`() {
        val clock = clockAt(ZoneId.of("Pacific/Auckland"), "2026-12-20T12:00:00")
        // 2026-10-31T12:00Z is already 2026-11-01 in Auckland (UTC+13 in summer).
        val section = sectionOf(Instant.parse("2026-10-31T12:00:00Z"), clock)
        assertEquals(NoteSection.Month(YearMonth.of(2026, 11), includesYear = false), section)
    }

    // --- sorting ------------------------------------------------------------------------------

    @Test
    fun `modified order is newest first with the local id as tiebreaker`() {
        val same = at("2026-10-15T08:00:00")
        val sorted = sortNotes(
            listOf(item(1, same), item(3, at("2026-10-15T09:00:00")), item(2, same)),
            NoteSortOrder.MODIFIED
        )
        assertEquals(listOf(3L, 2, 1), sorted.map { it.localId })
    }

    @Test
    fun `title order ignores case and falls back to newest first`() {
        val sorted = sortNotes(
            listOf(
                item(1, at("2026-10-01T00:00:00"), title = "banana"),
                item(2, at("2026-10-02T00:00:00"), title = "Apple"),
                item(3, at("2026-10-03T00:00:00"), title = "apple"),
                item(4, at("2026-10-04T00:00:00"), title = "Cherry")
            ),
            NoteSortOrder.TITLE
        )
        assertEquals(listOf(3L, 2, 1, 4), sorted.map { it.localId })
    }

    // --- grouping -----------------------------------------------------------------------------

    @Test
    fun `groups come in Apple Notes order with favorites pinned first`() {
        val notes = listOf(
            item(1, at("2025-03-05T10:00:00")),
            item(2, at("2026-10-15T09:00:00")),
            item(3, at("2026-10-14T09:00:00")),
            item(4, at("2026-10-10T09:00:00")),
            item(5, at("2026-09-25T09:00:00")),
            item(6, at("2026-08-02T09:00:00")),
            item(7, at("2026-01-01T09:00:00"), favorite = true),
            item(8, at("2026-10-15T10:00:00"), favorite = true)
        )
        val groups = buildNoteGroups(notes, FolderSelection.All, NoteSortOrder.MODIFIED, clockAt())
        assertEquals(
            listOf(
                NoteSection.Pinned,
                NoteSection.Today,
                NoteSection.Yesterday,
                NoteSection.Previous7Days,
                NoteSection.Previous30Days,
                NoteSection.Month(YearMonth.of(2026, 8), includesYear = false),
                NoteSection.Month(YearMonth.of(2025, 3), includesYear = true)
            ),
            groups.map { it.section }
        )
        assertEquals(listOf(8L, 7), groups.first().notes.map { it.localId })
        assertEquals(listOf(2L), groups[1].notes.map { it.localId })
    }

    @Test
    fun `a favorite appears only in the pinned group even if it was modified today`() {
        val notes = listOf(item(1, at("2026-10-15T09:00:00"), favorite = true))
        val groups = buildNoteGroups(notes, FolderSelection.All, NoteSortOrder.MODIFIED, clockAt())
        assertEquals(listOf(NoteSection.Pinned), groups.map { it.section })
    }

    @Test
    fun `the favorites folder groups by date instead of repeating a pinned header`() {
        val notes = listOf(
            item(1, at("2026-10-15T09:00:00"), favorite = true),
            item(2, at("2026-10-14T09:00:00"), favorite = true),
            item(3, at("2026-10-15T09:00:00"))
        )
        val groups =
            buildNoteGroups(notes, FolderSelection.Favorites, NoteSortOrder.MODIFIED, clockAt())
        assertEquals(listOf(NoteSection.Today, NoteSection.Yesterday), groups.map { it.section })
        assertEquals(listOf(1L), groups[0].notes.map { it.localId })
    }

    @Test
    fun `filtering applies before grouping and empty groups are dropped`() {
        val groups = buildNoteGroups(
            library,
            FolderSelection.Folder("Work"),
            NoteSortOrder.MODIFIED,
            clockAt()
        )
        assertEquals(
            listOf(NoteSection.Pinned, NoteSection.Previous7Days),
            groups.map { it.section }
        )
        assertEquals(listOf(2L, 3), groups.flatMap { it.notes }.map { it.localId })
    }

    @Test
    fun `title order has pinned notes then one alphabetical group`() {
        val notes = listOf(
            item(1, at("2026-10-15T09:00:00"), title = "zeta"),
            item(2, at("2025-01-15T09:00:00"), title = "alpha"),
            item(3, at("2026-10-01T09:00:00"), title = "mid", favorite = true)
        )
        val groups = buildNoteGroups(notes, FolderSelection.All, NoteSortOrder.TITLE, clockAt())
        assertEquals(
            listOf(NoteSection.Pinned, NoteSection.Alphabetical),
            groups.map {
                it.section
            }
        )
        assertEquals(listOf(2L, 1), groups[1].notes.map { it.localId })
    }

    @Test
    fun `nothing to show gives no groups`() {
        assertEquals(
            emptyList<NoteGroup>(),
            buildNoteGroups(emptyList(), FolderSelection.All, NoteSortOrder.TITLE, clockAt())
        )
    }
}
