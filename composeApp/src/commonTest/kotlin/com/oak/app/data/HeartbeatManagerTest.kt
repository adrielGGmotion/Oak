package com.oak.app.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeartbeatManagerTest {

    private fun freshManager(): HeartbeatManager {
        val appSettings = AppSettings(MapSettings())
        val memoryStore = MemoryStore(appSettings)
        val taskStore = TaskStore(appSettings)
        return HeartbeatManager(appSettings, memoryStore, taskStore)
    }

    @Test
    fun `getConfig returns defaults when no config saved`() {
        val manager = freshManager()
        val config = manager.getConfig()

        assertTrue(config.enabled)
        assertEquals(30, config.intervalMinutes)
        assertEquals(8, config.activeHoursStart)
        assertEquals(22, config.activeHoursEnd)
    }

    @Test
    fun `saveConfig persists the config`() {
        val manager = freshManager()
        val config = HeartbeatConfig(
            enabled = false,
            intervalMinutes = 60,
            activeHoursStart = 9,
            activeHoursEnd = 17,
            lastHeartbeatEpochMs = 12345L,
        )
        manager.saveConfig(config)

        val loaded = manager.getConfig()
        assertEquals(false, loaded.enabled)
        assertEquals(60, loaded.intervalMinutes)
        assertEquals(9, loaded.activeHoursStart)
        assertEquals(17, loaded.activeHoursEnd)
        assertEquals(12345L, loaded.lastHeartbeatEpochMs)
    }

    @Test
    fun `getHeartbeatLog returns empty initially`() {
        val manager = freshManager()
        assertTrue(manager.getHeartbeatLog().isEmpty())
    }

    @Test
    fun `recordHeartbeat adds entry to log`() {
        val manager = freshManager()
        manager.recordHeartbeat(success = true)
        manager.recordHeartbeat(success = false, error = "Connection failed")

        val log = manager.getHeartbeatLog()
        assertEquals(2, log.size)
        // Most recent entry is first (prepended)
        assertEquals(false, log[0].success)
        assertEquals("Connection failed", log[0].error)
        assertTrue(log[1].success)
    }

    @Test
    fun `recordHeartbeat caps log at MAX_LOG_ENTRIES`() {
        val manager = freshManager()
        repeat(10) { manager.recordHeartbeat(success = true) }

        val log = manager.getHeartbeatLog()
        // MAX_LOG_ENTRIES = 5 (private constant)
        assertEquals(5, log.size)
    }

    @Test
    fun `recordHeartbeat keeps most recent entries`() {
        val manager = freshManager()
        manager.recordHeartbeat(success = true)
        manager.recordHeartbeat(success = true)
        manager.recordHeartbeat(success = true)
        manager.recordHeartbeat(success = true)
        manager.recordHeartbeat(success = true)
        manager.recordHeartbeat(success = false, error = "Latest error")

        val log = manager.getHeartbeatLog()
        assertEquals(5, log.size)
        assertEquals(false, log[0].success)
        assertEquals("Latest error", log[0].error)
    }

    @Test
    fun `heartbeat log entries have timestamps`() {
        val manager = freshManager()
        manager.recordHeartbeat(success = true)

        val log = manager.getHeartbeatLog()
        assertEquals(1, log.size)
        assertTrue(log[0].timestampEpochMs > 0)
    }

    @Test
    fun `markHeartbeatExecuted updates lastHeartbeatEpochMs`() {
        val manager = freshManager()
        val config = HeartbeatConfig()
        manager.markHeartbeatExecuted(config)

        val loaded = manager.getConfig()
        assertTrue(loaded.lastHeartbeatEpochMs > 0)
    }
}
