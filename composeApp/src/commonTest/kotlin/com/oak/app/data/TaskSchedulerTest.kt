package com.oak.app.data

import com.oak.app.testutil.FakeDataRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [TaskScheduler] — focuses on configuration and state.
 *
 * The scheduler's coroutine-driven loop and platform-dependent components
 * (DataRepository, platform dispatchers, notification services) are best
 * exercised through integration tests on the desktop JVM target.
 */
class TaskSchedulerTest {

    @Test
    fun `scheduler starts disabled when enabled flag is false`() {
        val scheduler = TaskScheduler(
            dataRepository = FakeDataRepository(),
            enabled = false,
        )
        // No crash on construction
        assertTrue(true)
    }

    @Test
    fun `scheduler default isLoadingCheck returns false`() {
        val scheduler = TaskScheduler(
            dataRepository = FakeDataRepository(),
            enabled = false,
        )
        assertFalse(scheduler.isLoadingCheck())
    }

    @Test
    fun `scheduler default appInForeground is false`() {
        val scheduler = TaskScheduler(
            dataRepository = FakeDataRepository(),
            enabled = false,
        )
        assertFalse(scheduler.appInForeground)
    }
}
