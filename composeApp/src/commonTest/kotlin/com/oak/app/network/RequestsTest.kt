package com.oak.app.network

import com.oak.app.data.Service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [Requests] networking layer.
 *
 * Full integration tests requiring a live API are not included here.
 */
class RequestsTest {

    @Test
    fun `ServiceCredentials has sensible defaults`() {
        val creds = ServiceCredentials()
        assertEquals("", creds.apiKey)
        assertEquals("", creds.modelId)
        assertEquals("", creds.baseUrl)
    }

    @Test
    fun `ServiceCredentials can be constructed with values`() {
        val creds = ServiceCredentials(
            apiKey = "sk-test",
            modelId = "gpt-4",
            baseUrl = "https://api.example.com",
        )
        assertEquals("sk-test", creds.apiKey)
        assertEquals("gpt-4", creds.modelId)
        assertEquals("https://api.example.com", creds.baseUrl)
    }

    @Test
    fun `ServiceCredentials supports copy`() {
        val creds = ServiceCredentials(apiKey = "key1")
        val copy = creds.copy(modelId = "model2")
        assertEquals("key1", copy.apiKey)
        assertEquals("model2", copy.modelId)
    }

    @Test
    fun `ServiceCredentials is a data class`() {
        val a = ServiceCredentials(apiKey = "a")
        val b = ServiceCredentials(apiKey = "a")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
