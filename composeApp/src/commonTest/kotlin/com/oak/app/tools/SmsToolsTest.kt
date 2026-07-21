package com.oak.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmsToolsTest {

    @Test
    fun `smsToolDefinitions contains expected tools`() {
        val ids = SmsTools.smsToolDefinitions.map { it.id }.toSet()
        assertEquals(5, ids.size)
        assertTrue("check_sms" in ids)
        assertTrue("read_sms" in ids)
        assertTrue("search_sms" in ids)
        assertTrue("send_sms" in ids)
        assertTrue("reply_sms" in ids)
    }

    @Test
    fun `smsReadToolDefinitions contains read tools only`() {
        val ids = SmsTools.smsReadToolDefinitions.map { it.id }
        assertEquals(listOf("check_sms", "read_sms", "search_sms"), ids)
    }

    @Test
    fun `smsSendToolDefinitions contains send tools only`() {
        val ids = SmsTools.smsSendToolDefinitions.map { it.id }
        assertEquals(listOf("send_sms", "reply_sms"), ids)
    }

    @Test
    fun `all sms tool infos have non-empty fields`() {
        for (info in SmsTools.smsToolDefinitions) {
            assertTrue(info.name.isNotBlank(), "Name blank for ${info.id}")
            assertTrue(info.description.length >= 10, "Description too short for ${info.id}")
        }
    }
}
