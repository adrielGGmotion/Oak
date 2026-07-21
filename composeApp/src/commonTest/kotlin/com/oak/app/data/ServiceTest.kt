package com.oak.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceTest {

    @Test
    fun `fromId returns matching service`() {
        assertEquals(Service.Gemini, Service.fromId("gemini"))
        assertEquals(Service.Anthropic, Service.fromId("anthropic"))
        assertEquals(Service.OpenAI, Service.fromId("openai"))
        assertEquals(Service.Groq, Service.fromId("groqcloud"))
        assertEquals(Service.XAI, Service.fromId("xai"))
    }

    @Test
    fun `fromId returns OpenAICompatible for unknown id`() {
        assertEquals(Service.OpenAICompatible, Service.fromId("nonexistent"))
    }

    @Test
    fun `all contains every service singleton`() {
        assertTrue(Service.Gemini in Service.all)
        assertTrue(Service.Anthropic in Service.all)
        assertTrue(Service.OpenAI in Service.all)
        assertTrue(Service.LiteRT in Service.all)
    }

    @Test
    fun `service instances have unique ids`() {
        val ids = Service.all.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun `apiKeyKey follows expected pattern`() {
        assertEquals("service_gemini_api_key", Service.Gemini.apiKeyKey)
        assertEquals("service_anthropic_api_key", Service.Anthropic.apiKeyKey)
        assertEquals("service_openai_api_key", Service.OpenAI.apiKeyKey)
        assertEquals("service_litert_api_key", Service.LiteRT.apiKeyKey)
    }

    @Test
    fun `modelIdKey follows expected pattern`() {
        assertEquals("service_gemini_model_id", Service.Gemini.modelIdKey)
        assertEquals("service_openrouter_model_id", Service.OpenRouter.modelIdKey)
    }

    @Test
    fun `baseUrlKey follows expected pattern`() {
        assertEquals("service_openai-compatible_base_url", Service.OpenAICompatible.baseUrlKey)
    }

    @Test
    fun `Gemini has correct default properties`() {
        assertEquals("gemini", Service.Gemini.id)
        assertTrue(Service.Gemini.requiresApiKey)
        assertFalse(Service.Gemini.supportsOptionalApiKey)
        assertFalse(Service.Gemini.isOnDevice)
    }

    @Test
    fun `LiteRT is on-device and does not require api key`() {
        assertEquals("litert", Service.LiteRT.id)
        assertFalse(Service.LiteRT.requiresApiKey)
        assertTrue(Service.LiteRT.isOnDevice)
    }

    @Test
    fun `OpenAICompatible has correct defaults`() {
        assertEquals("openai-compatible", Service.OpenAICompatible.id)
        assertFalse(Service.OpenAICompatible.requiresApiKey)
        assertTrue(Service.OpenAICompatible.supportsOptionalApiKey)
        assertEquals("http://localhost:11434/v1", Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL)
    }

    @Test
    fun `OpenRouter supports PDF`() {
        assertTrue(Service.OpenRouter.supportsPdf)
    }

    @Test
    fun `apiKeyUrl is set for major providers`() {
        assertNotNull(Service.Groq.apiKeyUrl)
        assertNotNull(Service.Anthropic.apiKeyUrl)
        assertNotNull(Service.OpenAI.apiKeyUrl)
        assertNotNull(Service.XAI.apiKeyUrl)
    }

    @Test
    fun `fromId finds services with compound ids`() {
        assertEquals(Service.OpenAICompatible, Service.fromId("openai-compatible"))
        assertEquals(Service.GitHubModels, Service.fromId("github"))
        assertEquals(Service.SambaNova, Service.fromId("sambanova"))
    }
}
