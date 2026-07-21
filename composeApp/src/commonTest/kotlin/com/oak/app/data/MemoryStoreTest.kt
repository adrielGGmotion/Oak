package com.oak.app.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryStoreTest {

    private fun freshStore(): MemoryStore = MemoryStore(AppSettings(MapSettings()))

    @Test
    fun `store creates and retrieves a memory entry`() = runTest {
        val store = freshStore()
        val entry = store.store("test-key", "test content", MemoryCategory.LEARNING)

        assertEquals("test-key", entry.key)
        assertEquals("test content", entry.content)
        assertEquals(MemoryCategory.LEARNING, entry.category)
        assertEquals(1, entry.hitCount)
        assertTrue(entry.createdAt > 0)
        assertTrue(entry.updatedAt > 0)
    }

    @Test
    fun `store returns all stored memories`() = runTest {
        val store = freshStore()
        store.store("key1", "content1")
        store.store("key2", "content2", MemoryCategory.PREFERENCE)

        val all = store.getAllMemories()
        assertEquals(2, all.size)
    }

    @Test
    fun `store updates existing memory on same key`() = runTest {
        val store = freshStore()
        store.store("key1", "original")
        val updated = store.store("key1", "modified", MemoryCategory.ERROR)

        assertEquals("modified", updated.content)
        assertEquals(MemoryCategory.ERROR, updated.category)

        val all = store.getAllMemories()
        assertEquals(1, all.size)
    }

    @Test
    fun `updateContent modifies existing memory content`() = runTest {
        val store = freshStore()
        store.store("key1", "original")
        val updated = store.updateContent("key1", "modified content")

        assertNotNull(updated)
        assertEquals("modified content", updated.content)
    }

    @Test
    fun `updateContent returns null for missing key`() = runTest {
        val store = freshStore()
        assertNull(store.updateContent("nonexistent", "content"))
    }

    @Test
    fun `reinforceMemory increments hit count`() = runTest {
        val store = freshStore()
        store.store("key1", "content")
        val reinforced = store.reinforceMemory("key1")

        assertNotNull(reinforced)
        assertEquals(2, reinforced.hitCount)
    }

    @Test
    fun `reinforceMemory returns null for missing key`() = runTest {
        val store = freshStore()
        assertNull(store.reinforceMemory("nonexistent"))
    }

    @Test
    fun `forget removes memory and returns true`() = runTest {
        val store = freshStore()
        store.store("key1", "content")
        assertTrue(store.forget("key1"))
        assertTrue(store.getAllMemories().isEmpty())
    }

    @Test
    fun `forget returns false for missing key`() = runTest {
        val store = freshStore()
        assertEquals(false, store.forget("nonexistent"))
    }

    @Test
    fun `getPromotionCandidates returns memories with min hits`() = runTest {
        val store = freshStore()
        store.store("hits-1", "low")
        store.store("hits-5", "high")
        // reinforce hits-5 to 5 hits
        repeat(4) { store.reinforceMemory("hits-5") }

        val candidates = store.getPromotionCandidates(5)
        assertEquals(1, candidates.size)
        assertEquals("hits-5", candidates[0].key)
    }

    @Test
    fun `store supports source parameter`() = runTest {
        val store = freshStore()
        val entry = store.store("key1", "content", source = "user_query")
        assertEquals("user_query", entry.source)
    }

    @Test
    fun `initial store has empty memories`() = runTest {
        val store = freshStore()
        assertTrue(store.getAllMemories().isEmpty())
    }

    @Test
    fun `store defaults to GENERAL category`() = runTest {
        val store = freshStore()
        val entry = store.store("key1", "content")
        assertEquals(MemoryCategory.GENERAL, entry.category)
    }
}
