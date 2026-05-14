package com.beer.app.ui.markdown

import com.beer.app.ui.dynamicui.AlertNode
import com.beer.app.ui.dynamicui.ColumnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeerUiBlockIntegrationTest {

    @Test
    fun `beer-ui fence produces BeerUiBlock`() {
        val md = """
            ```beer-ui
            {"type":"alert","title":"Heads up","message":"Hello"}
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is BeerUiBlock)
        val alert = (block as BeerUiBlock).node as AlertNode
        assertEquals("Heads up", alert.title)
        assertEquals("Hello", alert.message)
    }

    @Test
    fun `malformed beer-ui fence produces BeerUiError`() {
        val md = """
            ```beer-ui
            not json at all
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is BeerUiError)
    }

    @Test
    fun `ndjson multi-line beer-ui wraps children in a column`() {
        val md = """
            ```beer-ui
            {"type":"text","value":"a"}
            {"type":"text","value":"b"}
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is BeerUiBlock)
        val col = (block as BeerUiBlock).node as ColumnNode
        assertEquals(2, col.children.size)
    }

    @Test
    fun `beer-ui block surrounded by markdown produces three blocks`() {
        val md = """
            Before

            ```beer-ui
            {"type":"alert","message":"hi"}
            ```

            After
        """.trimIndent()
        val blocks = parseMarkdown(md).blocks
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is Paragraph)
        assertTrue(blocks[1] is BeerUiBlock)
        assertTrue(blocks[2] is Paragraph)
    }

    @Test
    fun `split-block pattern with json fence is treated as beer-ui`() {
        val md = """
            beer-ui
            ```json
            {"type":"alert","message":"hi"}
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is BeerUiBlock)
    }

    @Test
    fun `beer-ui block speakable text walks the node tree`() {
        val md = """
            Intro.

            ```beer-ui
            {"type":"alert","title":"Heads up","message":"Take care"}
            ```

            Outro.
        """.trimIndent()
        val spoken = parseMarkdown(md).toSpeakableText()
        assertTrue(spoken.contains("Intro"))
        assertTrue(spoken.contains("Heads up"))
        assertTrue(spoken.contains("Take care"))
        assertTrue(spoken.contains("Outro"))
    }
}
