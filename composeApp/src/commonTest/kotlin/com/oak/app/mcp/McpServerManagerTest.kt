package com.oak.app.mcp

import com.oak.app.data.AppSettings
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpServerManagerTest {

    private fun freshManager(): McpServerManager = McpServerManager(AppSettings(MapSettings()))

    @Test
    fun `getServers returns empty initially`() {
        val manager = freshManager()
        assertTrue(manager.getServers().isEmpty())
    }

    @Test
    fun `addServer creates a new server config`() {
        val manager = freshManager()
        val config = manager.addServer(
            name = "Test Server",
            url = "https://example.com/mcp",
            headers = mapOf("Authorization" to "Bearer token"),
        )

        assertNotNull(config.id)
        assertEquals("Test Server", config.name)
        assertEquals("https://example.com/mcp", config.url)
        assertEquals("Bearer token", config.headers["Authorization"])
    }

    @Test
    fun `addServer generates unique ids for duplicate names`() {
        val manager = freshManager()
        manager.addServer(name = "My Server", url = "https://a.example", headers = emptyMap())
        manager.addServer(name = "My Server", url = "https://b.example", headers = emptyMap())

        val servers = manager.getServers()
        assertEquals(2, servers.size)
        assertTrue(servers[0].id != servers[1].id)
    }

    @Test
    fun `getServers reflects added servers`() {
        val manager = freshManager()
        manager.addServer(name = "Server A", url = "https://a.example", headers = emptyMap())
        manager.addServer(name = "Server B", url = "https://b.example", headers = emptyMap())

        val servers = manager.getServers()
        assertEquals(2, servers.size)
    }

    @Test
    fun `removeServer removes the server`() {
        val manager = freshManager()
        val config = manager.addServer(name = "Test", url = "https://example.com", headers = emptyMap())
        assertEquals(1, manager.getServers().size)

        manager.removeServer(config.id)
        assertTrue(manager.getServers().isEmpty())
    }

    @Test
    fun `setServerEnabled toggles enabled state`() {
        val manager = freshManager()
        val config = manager.addServer(name = "Test", url = "https://example.com", headers = emptyMap())
        assertTrue(config.isEnabled)

        manager.setServerEnabled(config.id, false)
        val servers = manager.getServers()
        assertFalse(servers[0].isEnabled)

        manager.setServerEnabled(config.id, true)
        assertTrue(manager.getServers()[0].isEnabled)
    }

    @Test
    fun `addServer defaults isEnabled to true`() {
        val manager = freshManager()
        val config = manager.addServer(name = "Test", url = "https://example.com", headers = emptyMap())
        assertTrue(config.isEnabled)
    }

    @Test
    fun `addServer defaults headers to empty`() {
        val manager = freshManager()
        val config = manager.addServer(name = "Test", url = "https://example.com", headers = emptyMap())
        assertTrue(config.headers.isEmpty())
    }

    @Test
    fun `getServers cache is invalidated on mutation`() {
        val manager = freshManager()
        manager.addServer(name = "A", url = "https://a.example", headers = emptyMap())
        assertEquals(1, manager.getServers().size)

        // Add another without re-fetching in-between
        manager.addServer(name = "B", url = "https://b.example", headers = emptyMap())
        assertEquals(2, manager.getServers().size)
    }

    @Test
    fun `addServer generates id from lowercase name`() {
        val manager = freshManager()
        val config = manager.addServer(name = "My Cool Server!", url = "https://example.com", headers = emptyMap())
        assertEquals("my_cool_server_", config.id)
    }
}
