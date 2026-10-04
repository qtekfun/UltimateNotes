// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.screenshots.DEMO_SETTINGS_FILE
import com.qtekfun.ultimatenotes.screenshots.DemoNotes
import com.qtekfun.ultimatenotes.sync.work.SyncStatusStore
import com.qtekfun.ultimatenotes.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Shared setup of the device-quality tests (T17b): the real [MainActivity] with an in-memory
 * database of made-up notes and an in-memory account (the Hilt replacements of
 * [com.qtekfun.ultimatenotes.screenshots.ScreenshotModules]), so nothing of the user's notes,
 * account or settings is touched and nothing goes to a network. Concrete tests carry
 * `@HiltAndroidTest` and `@UninstallModules(DatabaseModule, AuthModule, SettingsModule)`.
 */
abstract class DeviceTestBase {
    protected val hilt = HiltAndroidRule(this)
    protected val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var database: UltimateNotesDatabase

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var syncStatus: SyncStatusStore

    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val context: Context = instrumentation.targetContext
    protected var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUpBase() {
        hilt.inject()
        // Constraints are never met in the test driver, so nothing syncs while the UI is shown.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences(DEMO_SETTINGS_FILE, Context.MODE_PRIVATE).edit().clear()
            .commit()
        settings.setDynamicColor(false)
        settings.setTheme(ThemeMode.LIGHT)
        syncStatus.markSynced(Instant.now())
    }

    @After
    fun tearDownBase() {
        // A requested orientation outlives the test unless it is released.
        runCatching {
            val current = activity()
            instrumentation.runOnMainSync {
                current.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        scenario?.close()
    }

    /** The demo notes (English text), inserted into the in-memory database. */
    protected fun seedDemoNotes() {
        val clock = Clock.systemDefaultZone()
        runBlocking {
            DemoNotes.of("en", clock).notes.forEach {
                database.noteDao().insert(it.toEntity(clock))
            }
        }
    }

    protected fun launch(): ActivityScenario<MainActivity> {
        val started = ActivityScenario.launch(MainActivity::class.java)
        scenario = started
        compose.waitForIdle()
        return started
    }

    protected fun string(@StringRes id: Int, vararg args: Any): String =
        context.getString(id, *args)

    protected fun activity(): Activity {
        var current: Activity? = null
        requireNotNull(scenario).onActivity { current = it }
        return checkNotNull(current)
    }

    /** Waits for [condition], tolerating the instants where no composition is registered. */
    protected fun await(millis: Long = WAIT_MILLIS, condition: () -> Boolean) {
        compose.waitUntil(millis) {
            try {
                condition()
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    protected fun awaitText(text: String) = await { hasNodesWithText(text) }

    protected fun hasNodesWithText(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    protected fun hasNodesWithDescription(description: String): Boolean =
        compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    protected fun awaitDescription(description: String) =
        await { hasNodesWithDescription(description) }

    protected fun byDescription(@StringRes id: Int): SemanticsNodeInteraction =
        compose.onNodeWithContentDescription(string(id))

    protected fun byText(@StringRes id: Int, vararg args: Any): SemanticsNodeInteraction =
        compose.onNodeWithText(string(id, *args))

    protected fun clickableText(@StringRes id: Int): SemanticsNodeInteraction =
        compose.onNode(hasText(string(id)) and hasClickAction())

    protected fun textField(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction())

    protected fun openDrawer() {
        byDescription(R.string.folders_open).performClick()
        awaitText(string(R.string.folders_settings))
        settle()
    }

    protected fun closeDrawerByChoosingAllNotes() {
        clickableText(R.string.folder_all).performClick()
        settle()
    }

    /** System Back, as a user's gesture would. */
    protected fun pressBack() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        settle()
    }

    protected fun settle() {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)
        compose.waitForIdle()
    }

    protected fun keyboardVisible(): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            val root = ViewCompat.getRootWindowInsets(activity().window.decorView)
            visible = root?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    /** Rotates the activity as the system would (a configuration change: it is recreated). */
    protected fun rotate(landscape: Boolean) {
        val before = activity()
        val wanted = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        instrumentation.runOnMainSync { before.requestedOrientation = wanted }
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val now = activity()
            val isLandscape = now.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (now !== before && isLandscape == landscape) break
            Thread.sleep(POLL_MILLIS)
        }
        check(activity().resources.configuration.orientation == orientationOf(landscape)) {
            "The activity did not rotate"
        }
        settle()
    }

    private fun orientationOf(landscape: Boolean) = if (landscape) {
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    } else {
        android.content.res.Configuration.ORIENTATION_PORTRAIT
    }

    /** The whole screen of OUR app (demo notes only) into `Android/media/<package>/t17b/`. */
    protected fun shoot(name: String) {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "The screenshot failed"
        }
        File(outputDirectory(), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
    }

    @Suppress("DEPRECATION")
    private fun outputDirectory(): File {
        val root = context.externalMediaDirs.firstOrNull { it != null }
            ?: requireNotNull(context.getExternalFilesDir(null)) { "No external storage" }
        return File(root, "t17b").apply { mkdirs() }
    }

    protected companion object {
        const val WAIT_MILLIS = 15_000L
        const val SETTLE_MILLIS = 600L
        const val POLL_MILLIS = 100L
        const val PNG_QUALITY = 100
    }
}
