package com.oak.app.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [McpClient] JSON-RPC protocol handling.
 * Full integration tests require a running MCP server and are not included here.
 */
class McpClientTest {

    @Test
    fun `McpException carries message`() {
        val ex = McpException("Test error")
        assertEquals("Test error", ex.message)
    }

    @Test
    fun `JsonRpcRequest serialization`() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val params = buildJsonObject { put("key", "value") }
        val request = JsonRpcRequest(id = 1, method = "tools/list", params = params)

        val encoded = json.encodeToString(JsonRpcRequest.serializer(), request)
        assertNotNull(encoded)
        assertTrue(encoded.contains("\"id\":1"))
        assertTrue(encoded.contains("\"method\":\"tools/list\""))
        assertTrue(encoded.contains("\"params\""))
    }

    @Test
    fun `JsonRpcResponse with result`() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val responseJson = """{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}"""
        val response = json.decodeFromString(JsonRpcResponse.serializer(), responseJson)

        assertNotNull(response.result)
        assertEquals(null, response.error)
    }

    @Test
    fun `JsonRpcResponse with error`() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val responseJson = """{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}"""
        val response = json.decodeFromString(JsonRpcResponse.serializer(), responseJson)

        assertNotNull(response.error)
        assertEquals(-32601, response.error.code)
        assertEquals("Method not found", response.error.message)
    }
}
