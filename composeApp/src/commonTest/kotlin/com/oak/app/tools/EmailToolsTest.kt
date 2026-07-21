package com.oak.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailToolsTest {

    @Test
    fun `emailToolDefinitions contains expected tools`() {
        val ids = EmailTools.emailToolDefinitions.map { it.id }.toSet()
        assertEquals(6, ids.size)
        assertTrue("setup_email" in ids)
        assertTrue("check_email" in ids)
        assertTrue("read_email" in ids)
        assertTrue("reply_email" in ids)
        assertTrue("compose_email" in ids)
        assertTrue("search_email" in ids)
    }

    @Test
    fun `all email tool infos have non-empty fields`() {
        for (info in EmailTools.emailToolDefinitions) {
            assertTrue(info.name.isNotBlank(), "Name blank for ${info.id}")
            assertTrue(info.description.length >= 10, "Description too short for ${info.id}")
        }
    }

    @Test
    fun `individual tool infos match expected ids`() {
        assertEquals("setup_email", EmailTools.setupEmailToolInfo.id)
        assertEquals("check_email", EmailTools.checkEmailToolInfo.id)
        assertEquals("read_email", EmailTools.readEmailToolInfo.id)
        assertEquals("reply_email", EmailTools.replyEmailToolInfo.id)
        assertEquals("compose_email", EmailTools.composeEmailToolInfo.id)
        assertEquals("search_email", EmailTools.searchEmailToolInfo.id)
    }
}
