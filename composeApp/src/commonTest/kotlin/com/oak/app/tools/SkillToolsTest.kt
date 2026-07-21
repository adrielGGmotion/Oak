package com.oak.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillToolsTest {

    @Test
    fun `skillToolDefinitions contains expected tools`() {
        val ids = SkillTools.skillToolDefinitions.map { it.id }.toSet()
        assertTrue("load_skill" in ids)
        assertTrue("unload_skill" in ids)
        assertTrue("list_skills" in ids)
        assertTrue("create_skill" in ids)
        assertTrue("update_skill" in ids)
    }

    @Test
    fun `all skill tools have non-empty names and descriptions`() {
        for (tool in SkillTools.skillToolDefinitions) {
            assertTrue(tool.id.isNotBlank(), "Tool id is blank")
            assertTrue(tool.name.isNotBlank(), "Tool name is blank for ${tool.id}")
        }
    }

    @Test
    fun `generateSkillIdFromName generates valid id from simple name`() {
        val id = generateSkillIdFromName("My Skill", emptySet())
        assertEquals("my_skill", id)
    }

    @Test
    fun `generateSkillIdFromName handles special characters`() {
        val id = generateSkillIdFromName("Hello World!!!", emptySet())
        assertEquals("hello_world", id)
    }

    @Test
    fun `generateSkillIdFromName handles multiple spaces and underscores`() {
        val id = generateSkillIdFromName("  lots   of   spaces  ", emptySet())
        assertEquals("lots_of_spaces", id)
    }

    @Test
    fun `generateSkillIdFromName falls back to skill for blank input`() {
        val id = generateSkillIdFromName("_", emptySet())
        assertEquals("skill", id)
    }

    @Test
    fun `generateSkillIdFromName appends counter for duplicates`() {
        val id = generateSkillIdFromName("my_skill", setOf("my_skill"))
        assertEquals("my_skill_2", id)
    }

    @Test
    fun `generateSkillIdFromName increments counter for multiple duplicates`() {
        val id = generateSkillIdFromName("skill", setOf("skill", "skill_2", "skill_3"))
        assertEquals("skill_4", id)
    }

    @Test
    fun `generateSkillIdFromName trims to 50 chars`() {
        val longName = "a" + "_b".repeat(50)
        val id = generateSkillIdFromName(longName, emptySet())
        assertTrue(id.length <= 50)
    }

    @Test
    fun `generateSkillIdFromName handles empty existing ids set`() {
        val id = generateSkillIdFromName("test", emptySet())
        assertEquals("test", id)
    }
}
