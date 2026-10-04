// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.screenshots

import android.accessibilityservice.AccessibilityService
import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.auth.AuthModule
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.di.DatabaseModule
import com.qtekfun.ultimatenotes.di.SettingsModule
import com.qtekfun.ultimatenotes.sync.work.SyncStatusStore
import com.qtekfun.ultimatenotes.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Takes the F-Droid phone screenshots of the real app UI, in English and Spanish, with made-up
 * notes in an in-memory database and a signed-in account that exists only in memory (see
 * [ScreenshotModules]). It is not a test of behavior: it only fails when the UI cannot be
 * driven. Run it with `scripts/capture-screenshots.sh`, never as part of a normal test run.
 *
 * The PNGs go to `Android/media/<package>/screenshots/<locale>/N_name.png` on the device.
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class, AuthModule::class, SettingsModule::class)
class ScreenshotTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var database: UltimateNotesDatabase

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var syncStatus: SyncStatusStore

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousLocales: LocaleList = LocaleList.getEmptyLocaleList()

    @Before
    fun setUp() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "Screenshots switch the language with per-app locales: Android 13 or newer needed"
        }
        hilt.inject()
        // Constraints are never met in the test driver, so nothing syncs while the UI is shown.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences(DEMO_SETTINGS_FILE, Context.MODE_PRIVATE).edit().clear()
            .commit()
        // The system look is not ours to choose: fixed colors, light until the dark screenshot.
        settings.setDynamicColor(false)
        settings.setTheme(ThemeMode.LIGHT)
        // The last sync error is kept in the app's own preferences (a previous run on this device
        // may have left one, which would paint the sync button red): show a clean sync instead.
        syncStatus.markSynced(Instant.now())
        localeManager().let { previousLocales = it.applicationLocales }
    }

    @After
    fun tearDown() {
        scenario?.close()
        localeManager().applicationLocales = previousLocales
    }

    @Test
    fun englishScreenshots() = capture("en-US", "en")

    @Test
    fun spanishScreenshots() = capture("es-ES", "es")

    private fun capture(directory: String, language: String) {
        val demo = DemoNotes.of(language, Clock.systemDefaultZone())
        runBlocking {
            demo.notes.forEach { database.noteDao().insert(it.toEntity(Clock.systemDefaultZone())) }
        }
        val output = outputDirectory(directory)
        localeManager().applicationLocales = LocaleList.forLanguageTags(language)
        launch(language)

        // 1. The list, with its dated sections.
        awaitText(R.string.section_pinned)
        shoot(output, "1_list")

        // 2. The folder drawer, with folders and counts.
        compose.onNodeWithContentDescription(string(R.string.folders_open)).performClick()
        awaitText(R.string.folders_settings)
        shoot(output, "2_folders")
        // Choosing "All notes" (already selected) closes the drawer, like tapping any folder.
        compose.onNode(hasText(string(R.string.folder_all)) and hasClickAction()).performClick()
        // The drawer stays composed when closed, so there is nothing to wait for: let it slide.
        compose.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)

        // 3. The editor: headings, bold, italic and a checklist with some items checked.
        compose.onNodeWithText(demo.editorTitle).performClick()
        awaitGone(R.string.section_pinned)
        shoot(output, "3_editor")
        pressBack()
        awaitText(R.string.section_pinned)

        // 4. The search results, with the highlighted match and the bottom bar.
        compose.onNodeWithContentDescription(string(R.string.search_field)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(demo.query)
        // The search waits for a pause in the typing before it runs.
        Thread.sleep(SEARCH_MILLIS)
        compose.waitForIdle()
        hideKeyboard()
        shoot(output, "4_search")
        compose.onNodeWithContentDescription(string(R.string.search_close)).performClick()
        awaitText(R.string.section_pinned)

        // 5. The list again, in the dark theme.
        settings.setTheme(ThemeMode.DARK)
        compose.waitForIdle()
        shoot(output, "5_dark")
    }

    private fun launch(language: String) {
        val started = ActivityScenario.launch(MainActivity::class.java)
        scenario = started
        // The new language reaches the activity asynchronously; recreate once if it was late.
        if (activityLanguage() != language) {
            started.recreate()
            compose.waitForIdle()
        }
        assertEquals("The UI is not in the requested language", language, activityLanguage())
    }

    private fun activityLanguage(): String {
        var language = ""
        onActivity { language = it.resources.configuration.locales[0].language }
        return language
    }

    private fun onActivity(block: (MainActivity) -> Unit) {
        requireNotNull(scenario).onActivity(block)
    }

    private fun localeManager(): LocaleManager =
        requireNotNull(context.getSystemService(LocaleManager::class.java))

    /** A string of the language the activity is showing, which is not always the context's. */
    private fun string(@StringRes id: Int): String {
        var text = ""
        onActivity { text = it.getString(id) }
        return text
    }

    private fun awaitText(@StringRes id: Int) {
        val text = string(id)
        compose.waitUntil(WAIT_MILLIS) {
            hasNodes(text)
        }
    }

    /**
     * Whether a node with [text] is on screen. Right after a launch or a recreation the root's
     * composition is not registered with the test rule yet, which it reports as an exception.
     */
    private fun hasNodes(text: String): Boolean = try {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    private fun awaitGone(@StringRes id: Int) {
        val text = string(id)
        compose.waitUntil(WAIT_MILLIS) {
            !hasNodes(text)
        }
    }

    /** The system Back, as a user's gesture would (drawer, editor and search all listen to it). */
    private fun pressBack() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)
    }

    private fun hideKeyboard() {
        onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitForIdle()
    }

    private fun outputDirectory(directory: String): File {
        @Suppress("DEPRECATION")
        val root = context.externalMediaDirs.firstOrNull { it != null }
            ?: requireNotNull(context.getExternalFilesDir(null)) { "No external storage" }
        return File(root, "screenshots/$directory").apply {
            deleteRecursively()
            check(mkdirs()) { "Cannot create the screenshots directory" }
        }
    }

    /** Captures the whole screen (system demo mode hides the personal parts of the status bar). */
    private fun shoot(directory: File, name: String) {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "The screenshot failed"
        }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
        const val SETTLE_MILLIS = 600L
        const val SEARCH_MILLIS = 1_000L
        const val PNG_QUALITY = 100
    }
}
