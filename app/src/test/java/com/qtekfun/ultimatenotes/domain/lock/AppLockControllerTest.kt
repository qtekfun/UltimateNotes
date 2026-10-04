// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest {
    private val clock = MutableClock(1_000_000L)
    private val store = FakeLockStore()
    private val source = FakeConfigSource()

    private fun controller() = AppLockController(LockPolicy(clock), store, source)

    private fun enable(timeout: LockTimeout = LockTimeout.FIVE_MINUTES) {
        source.config = LockConfig(enabled = true, timeout = timeout)
    }

    private fun prompt(result: AuthResult) = LockPrompt { result }

    @Test
    fun `with the lock off the app starts unlocked`() {
        assertFalse(controller().locked.value)
    }

    @Test
    fun `a cold start with the lock on is locked`() {
        enable()
        assertTrue(controller().locked.value)
    }

    @Test
    fun `a process restored within the timeout stays unlocked`() {
        enable()
        store.backgroundedAt = clock.millis - 60_000
        assertFalse(controller().locked.value)
    }

    @Test
    fun `a process restored after the timeout is locked`() {
        enable()
        store.backgroundedAt = clock.millis - 300_000
        assertTrue(controller().locked.value)
    }

    @Test
    fun `a process restored after the clock went backwards is locked`() {
        enable()
        store.backgroundedAt = clock.millis + 5_000
        assertTrue(controller().locked.value)
    }

    @Test
    fun `a successful prompt unlocks and forgets the timestamp`() = runTest {
        enable()
        val controller = controller()
        assertEquals(AuthResult.Success, controller.authenticate(prompt(AuthResult.Success)))
        assertFalse(controller.locked.value)
        assertNull(store.backgroundedAt)
    }

    @Test
    fun `a cancelled prompt stays locked`() = runTest {
        enable()
        val controller = controller()
        assertEquals(AuthResult.Cancelled, controller.authenticate(prompt(AuthResult.Cancelled)))
        assertTrue(controller.locked.value)
        assertEquals(0, source.disabled)
    }

    @Test
    fun `when nothing can authenticate any more the lock is switched off and the app opens`() =
        runTest {
            enable()
            val controller = controller()
            assertEquals(
                AuthResult.Unavailable,
                controller.authenticate(prompt(AuthResult.Unavailable))
            )
            assertFalse(controller.locked.value)
            assertEquals(1, source.disabled)
            assertFalse(source.config.enabled)
        }

    @Test
    fun `leaving and returning inside the timeout does not lock`() {
        enable(LockTimeout.FIVE_MINUTES)
        val controller = unlockedController()
        controller.onEnterBackground()
        assertEquals(clock.millis, store.backgroundedAt)
        clock.advance(299_999)
        controller.onEnterForeground()
        assertFalse(controller.locked.value)
        assertNull(store.backgroundedAt)
    }

    @Test
    fun `returning at the timeout locks`() {
        enable(LockTimeout.ONE_MINUTE)
        val controller = unlockedController()
        controller.onEnterBackground()
        clock.advance(60_000)
        controller.onEnterForeground()
        assertTrue(controller.locked.value)
    }

    @Test
    fun `immediately locks on every return`() {
        enable(LockTimeout.IMMEDIATELY)
        val controller = unlockedController()
        controller.onEnterBackground()
        controller.onEnterForeground()
        assertTrue(controller.locked.value)
    }

    @Test
    fun `a locked app that goes to the background again keeps the first timestamp unset`() {
        enable(LockTimeout.FIFTEEN_MINUTES)
        val controller = controller()
        assertTrue(controller.locked.value)
        controller.onEnterBackground()
        assertNull(store.backgroundedAt)
        clock.advance(1)
        controller.onEnterForeground()
        assertTrue(controller.locked.value)
    }

    @Test
    fun `a foreground process that dies restores locked`() {
        enable(LockTimeout.FIFTEEN_MINUTES)
        val first = unlockedController()
        first.onEnterForeground()
        // The process dies here, in the foreground: no timestamp was ever stored.
        assertNull(store.backgroundedAt)
        assertTrue(controller().locked.value)
    }

    @Test
    fun `a background process that dies restores by the time spent away`() {
        enable(LockTimeout.FIVE_MINUTES)
        unlockedController().onEnterBackground()
        clock.advance(299_999)
        assertFalse(controller().locked.value)
        clock.advance(1)
        assertTrue(controller().locked.value)
    }

    @Test
    fun `lifecycle events are ignored while the prompt is showing`() = runTest {
        enable(LockTimeout.IMMEDIATELY)
        val controller = controller()
        val gate = CompletableDeferred<AuthResult>()
        val job = launch { controller.authenticate { gate.await() } }
        runCurrent()
        // The credential screen of an older Android takes the activity to the background.
        controller.onEnterBackground()
        controller.onEnterForeground()
        gate.complete(AuthResult.Success)
        job.join()
        assertFalse(controller.locked.value)
        assertNull(store.backgroundedAt)
    }

    @Test
    fun `a second prompt while one is showing is refused`() = runTest {
        enable()
        val controller = controller()
        val gate = CompletableDeferred<AuthResult>()
        val first = launch { controller.authenticate { gate.await() } }
        runCurrent()
        assertEquals(AuthResult.Cancelled, controller.authenticate(prompt(AuthResult.Success)))
        assertTrue(controller.locked.value)
        gate.complete(AuthResult.Cancelled)
        first.join()
    }

    @Test
    fun `the prompt can run again after it failed with an exception`() = runTest {
        enable()
        val controller = controller()
        runCatching { controller.authenticate { error("boom") } }
        assertEquals(AuthResult.Success, controller.authenticate(prompt(AuthResult.Success)))
        assertFalse(controller.locked.value)
    }

    @Test
    fun `switching the lock off unlocks and clears the timestamp`() {
        enable()
        val controller = controller()
        store.backgroundedAt = 5
        controller.onConfigChanged(LockConfig(enabled = false))
        assertFalse(controller.locked.value)
        assertNull(store.backgroundedAt)
    }

    @Test
    fun `switching the lock on while using the app does not lock it`() {
        val controller = controller()
        source.config = LockConfig(enabled = true, timeout = LockTimeout.IMMEDIATELY)
        controller.onConfigChanged(source.config)
        assertFalse(controller.locked.value)
        // ...but leaving now is tracked.
        controller.onEnterBackground()
        assertEquals(clock.millis, store.backgroundedAt)
    }

    @Test
    fun `a timeout changed while away applies on return`() {
        enable(LockTimeout.FIFTEEN_MINUTES)
        val controller = unlockedController()
        controller.onEnterBackground()
        clock.advance(120_000)
        controller.onConfigChanged(LockConfig(true, LockTimeout.ONE_MINUTE))
        controller.onEnterForeground()
        assertTrue(controller.locked.value)
    }

    @Test
    fun `the lock state is readable through the AppLockState interface`() {
        enable()
        val state: AppLockState = controller()
        assertTrue(state.locked.value)
    }

    private fun unlockedController(): AppLockController {
        val controller = controller()
        runUnlocked(controller)
        return controller
    }

    private fun runUnlocked(controller: AppLockController) {
        if (controller.locked.value) {
            runTest { controller.authenticate(prompt(AuthResult.Success)) }
        }
    }
}
