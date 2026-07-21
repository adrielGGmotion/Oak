package com.oak.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommonToolsTest {

    @Test
    fun `commonToolDefinitions is not empty`() {
        assertTrue(CommonTools.commonToolDefinitions.isNotEmpty())
    }

    @Test
    fun `commonToolDefinitions contain expected tools`() {
        val ids = CommonTools.commonToolDefinitions.map { it.id }.toSet()
        assertTrue("get_local_time" in ids)
        assertTrue("get_location_from_ip" in ids)
        assertTrue("web_search" in ids)
        assertTrue("open_url" in ids)
        assertTrue("wait" in ids)
        assertTrue("memory_store" in ids)
        assertTrue("memory_forget" in ids)
        assertTrue("memory_reinforce" in ids)
        assertTrue("memory_learn" in ids)
        assertTrue("compress_context" in ids)
        assertTrue("fetch_url" in ids)
        assertTrue("ask_questions" in ids)
        assertTrue("schedule_task" in ids)
        assertTrue("promote_learning" in ids)
        assertTrue("setup_email" in ids)
        assertTrue("check_sms" in ids)
    }

    @Test
    fun `masterToggleControlledToolIds is not empty`() {
        assertTrue(CommonTools.masterToggleControlledToolIds.isNotEmpty())
    }

    @Test
    fun `all tool definitions have non-empty ids and names`() {
        for (tool in CommonTools.commonToolDefinitions) {
            assertTrue(tool.id.isNotBlank(), "Tool id is blank")
            assertTrue(tool.name.isNotBlank(), "Tool name is blank for ${tool.id}")
        }
    }
}
