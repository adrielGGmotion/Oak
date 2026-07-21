package com.oak.app.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Supplemental tests for [BlockScanner] covering block types not exercised by
 * [BlockParsingTest]. Focuses on the scanner's ability to classify line types and
 * produce the correct [BlockNode] subtypes via [parseMarkdown] (which calls [BlockScanner]).
 */
class BlockScannerSupplementTest {

    @Test
    fun `fenced code block with backticks`() {
        val doc = parseMarkdown("```\ncode\n```")
        val block = doc.blocks.single() as CodeFence
        assertEquals("code", block.code)
        assertTrue(block.closed)
    }

    @Test
    fun `fenced code block with tildes`() {
        val doc = parseMarkdown("~~~\ncode\n~~~")
        val block = doc.blocks.single() as CodeFence
        assertEquals("code", block.code)
    }

    @Test
    fun `fenced code block with language info string`() {
        val doc = parseMarkdown("```kotlin\nval x = 1\n```")
        val block = doc.blocks.single() as CodeFence
        assertEquals("kotlin", block.language)
        assertEquals("val x = 1", block.code)
    }

    @Test
    fun `unclosed fenced code block`() {
        val doc = parseMarkdown("```\nopen code")
        val block = doc.blocks.single() as CodeFence
        assertEquals("open code", block.code)
        assertTrue(!block.closed)
    }

    @Test
    fun `horizontal rule with dashes`() {
        val doc = parseMarkdown("---")
        assertIs<HorizontalRule>(doc.blocks.single())
    }

    @Test
    fun `horizontal rule with asterisks`() {
        val doc = parseMarkdown("***")
        assertIs<HorizontalRule>(doc.blocks.single())
    }

    @Test
    fun `horizontal rule with underscores`() {
        val doc = parseMarkdown("___")
        assertIs<HorizontalRule>(doc.blocks.single())
    }

    @Test
    fun `blockquote simple`() {
        val doc = parseMarkdown("> quote")
        val bq = doc.blocks.single() as Blockquote
        assertEquals(1, bq.children.size)
    }

    @Test
    fun `blockquote multiline`() {
        val doc = parseMarkdown("> line1\n> line2")
        val bq = doc.blocks.single() as Blockquote
        assertTrue(bq.children.size >= 1)
    }

    @Test
    fun `bullet list unordered`() {
        val doc = parseMarkdown("- item1\n- item2")
        val list = doc.blocks.single() as BulletList
        assertEquals(2, list.items.size)
    }

    @Test
    fun `bullet list with asterisk marker`() {
        val doc = parseMarkdown("* item1\n* item2")
        val list = doc.blocks.single() as BulletList
        assertEquals(2, list.items.size)
    }

    @Test
    fun `ordered list`() {
        val doc = parseMarkdown("1. first\n2. second")
        val list = doc.blocks.single() as OrderedList
        assertEquals(2, list.items.size)
        assertEquals(1, list.start)
    }

    @Test
    fun `setext heading level 1`() {
        val doc = parseMarkdown("Title\n=====")
        val heading = doc.blocks.single() as Heading
        assertEquals(1, heading.level)
    }

    @Test
    fun `setext heading level 2`() {
        val doc = parseMarkdown("Title\n-----")
        val heading = doc.blocks.single() as Heading
        assertEquals(2, heading.level)
    }

    @Test
    fun `paragraph text`() {
        val doc = parseMarkdown("Just a paragraph.")
        assertIs<Paragraph>(doc.blocks.single())
    }

    @Test
    fun `multiple paragraphs separated by blank line`() {
        val doc = parseMarkdown("First para.\n\nSecond para.")
        assertEquals(2, doc.blocks.size)
        assertIs<Paragraph>(doc.blocks[0])
        assertIs<Paragraph>(doc.blocks[1])
    }

    @Test
    fun `gfm table with pipe delimiters`() {
        val doc = parseMarkdown("| H1 | H2 |\n| --- | --- |\n| C1 | C2 |")
        assertTrue(doc.blocks.isNotEmpty())
    }
}
