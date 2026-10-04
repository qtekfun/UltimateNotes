// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimatenotes.data.auth.AuthModule
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSearchDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.di.DatabaseModule
import com.qtekfun.ultimatenotes.di.SettingsModule
import com.qtekfun.ultimatenotes.screenshots.DemoDatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T17b, the feel of 5 000 notes (SPEC §10) in the real UI over a real file database of synthetic
 * notes (never the user's database): the time from launching the activity to the first list row,
 * the time to open the folder drawer, to the first search result, and the frame times while
 * flinging the list up and down (FrameMetrics of the window, cross-checked with `dumpsys gfxinfo`).
 * Run it alone (`-e class ...BigLibraryUiTest`) right after force-stopping the app for a cold
 * process. Numbers go to logcat as `T17B big ...`.
 */
@HiltAndroidTest
@UninstallModules(
    DatabaseModule::class,
    DemoDatabaseModule::class,
    AuthModule::class,
    SettingsModule::class
)
class BigLibraryUiTest : DeviceTestBase() {
    @Module
    @InstallIn(SingletonComponent::class)
    object FileDatabaseModule {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): UltimateNotesDatabase {
            context.deleteDatabase(NAME)
            return Room.databaseBuilder<UltimateNotesDatabase>(context, NAME)
                .setDriver(AndroidSQLiteDriver())
                .build()
        }

        @Provides
        fun noteDao(database: UltimateNotesDatabase): NoteDao = database.noteDao()

        @Provides
        fun noteSearchDao(database: UltimateNotesDatabase): NoteSearchDao = database.noteSearchDao()

        @Provides
        fun noteSyncDao(database: UltimateNotesDatabase): NoteSyncDao = database.noteSyncDao()
    }

    @org.junit.After
    fun deleteTheDatabase() {
        database.close()
        context.deleteDatabase(NAME)
    }

    /** `-e notes 500` runs the same flow on a smaller library, to see the curve. */
    private val notesCount: Int = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        .getString("notes")?.toInt() ?: DEFAULT_NOTES

    private fun log(message: String) = Log.i(T17B_TAG, "big $message")

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }
    }

    @Test
    @Suppress("LongMethod")
    fun fiveThousandNotesColdListDrawerSearchAndScroll() {
        val seeded = SystemClock.elapsedRealtime()
        runBlocking {
            repeat(notesCount) { database.noteDao().insert(SyntheticNotes.entity(it, notesCount)) }
        }
        log("seeded $notesCount notes in ${SystemClock.elapsedRealtime() - seeded} ms")

        // The seeding takes ~10 s: make sure the screen is on (a sleeping display shows nothing).
        shell("input keyevent KEYCODE_WAKEUP")
        Thread.sleep(SETTLE_MILLIS)
        val newest = SyntheticNotes.title(0)
        val started = SystemClock.elapsedRealtime()
        launch()
        try {
            await(LIST_BUDGET_MS) { hasNodesWithText(newest) }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            shoot("big_0_list_timeout")
            throw timeout
        }
        val firstList = SystemClock.elapsedRealtime() - started
        log("launch to first list row: $firstList ms")
        log(
            "process start to first list row (includes the seeding above): " +
                "${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms"
        )
        shoot("big_1_list")

        // Frame times while flinging up and down.
        val durations = mutableListOf<Long>()
        val handler = Handler(Looper.getMainLooper())
        val window: Window = activity().window
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            synchronized(durations) {
                durations += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
        }
        val refresh = activity().display.refreshRate
        shell("dumpsys gfxinfo ${context.packageName} reset")
        instrumentation.runOnMainSync {
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }
        // Flings through the Compose test injector. It waits for idle between gestures, so the
        // frame times include the test harness: read them as an upper bound (see ADR 0010).
        val list = compose.onAllNodes(hasScrollAction()).onFirst()
        repeat(SWIPES) {
            list.performTouchInput { swipeUp(durationMillis = SWIPE_MS) }
            compose.waitForIdle()
        }
        repeat(SWIPES) {
            list.performTouchInput { swipeDown(durationMillis = SWIPE_MS) }
            compose.waitForIdle()
        }
        Thread.sleep(SETTLE_AFTER_SCROLL)
        instrumentation.runOnMainSync { window.removeOnFrameMetricsAvailableListener(listener) }
        val gfx = shell("dumpsys gfxinfo ${context.packageName}")
        gfx.lineSequence().filter {
            it.startsWith("Total frames") || it.startsWith("Janky frames") ||
                it.contains("th percentile")
        }.forEach { log("gfxinfo: ${it.trim()}") }
        val frames = synchronized(durations) { durations.toList() }
        val budget = (1_000_000_000.0 / refresh).toLong()
        val sorted = frames.sorted()
        fun percentile(p: Int) = sorted[(sorted.size - 1) * p / PERCENT] / NANOS_PER_MILLI
        val janky = frames.count { it > budget }
        log(
            "scroll: ${frames.size} frames at ${refresh.toInt()} Hz (budget " +
                "%.1f ms), janky %d = %.1f%%, p50 %.1f p90 %.1f p95 %.1f p99 %.1f ms, max %.1f ms"
                    .format(
                        budget / NANOS_PER_MILLI,
                        janky,
                        100.0 * janky / frames.size,
                        percentile(P50),
                        percentile(P90),
                        percentile(P95),
                        percentile(P99),
                        sorted.last() / NANOS_PER_MILLI
                    )
        )
        assertTrue("no frames were measured", frames.size > MIN_FRAMES)

        // Folder drawer with the whole tree.
        val drawer = SystemClock.elapsedRealtime()
        openDrawer()
        log(
            "tap to drawer content: ${SystemClock.elapsedRealtime() - drawer - SETTLE_MILLIS} ms (incl. settle)"
        )
        shoot("big_2_drawer")
        closeDrawerByChoosingAllNotes()

        // First search result.
        byDescription(com.qtekfun.ultimatenotes.R.string.search_field).performClick()
        val typed = SystemClock.elapsedRealtime()
        compose.onNode(hasSetTextAction()).performTextInput(SyntheticNotes.SEARCH_WORD)
        try {
            // The field itself shows the word once; result rows carry it again in their highlights.
            await {
                compose.onAllNodesWithText(SyntheticNotes.SEARCH_WORD, substring = true)
                    .fetchSemanticsNodes().size >= MIN_RESULT_NODES
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            shoot("big_4_search_timeout")
            throw timeout
        }
        log(
            "typing a word to first result: ${SystemClock.elapsedRealtime() - typed} ms (incl. debounce)"
        )
        shoot("big_3_search")
        assertTrue("the first list took ${firstList / 1000.0} s", firstList < LIST_BUDGET_MS)
    }

    private companion object {
        const val NAME = "t17b-ui-5000.db"
        const val DEFAULT_NOTES = 5_000
        const val SWIPES = 15
        const val MIN_RESULT_NODES = 3
        const val SWIPE_MS = 250L
        const val SETTLE_AFTER_SCROLL = 500L
        const val PERCENT = 100
        const val P50 = 50
        const val P90 = 90
        const val P95 = 95
        const val P99 = 99
        const val MIN_FRAMES = 20
        const val NANOS_PER_MILLI = 1_000_000.0
        const val LIST_BUDGET_MS = 10_000L
    }
}
