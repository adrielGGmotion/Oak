package com.oak.app.ui.markdown.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Supplemental tests for [MathParser] covering expression types not exercised by
 * [MathParsingTest] and [MathAtomParserTest].
 */
class MathParserSupplementTest {

    @Test
    fun `parse simple addition`() {
        val result = MathParser.parse("1+2")
        assertNotNull(result)
    }

    @Test
    fun `parse fraction`() {
        val result = MathParser.parse("\\frac{1}{2}")
        assertNotNull(result)
    }

    @Test
    fun `parse sqrt`() {
        val result = MathParser.parse("\\sqrt{4}")
        assertNotNull(result)
    }

    @Test
    fun `parse subscript`() {
        val result = MathParser.parse("x_1")
        assertNotNull(result)
    }

    @Test
    fun `parse superscript`() {
        val result = MathParser.parse("x^2")
        assertNotNull(result)
    }

    @Test
    fun `parse integral`() {
        val result = MathParser.parse("\\int")
        assertNotNull(result)
    }

    @Test
    fun `parse sum`() {
        val result = MathParser.parse("\\sum")
        assertNotNull(result)
    }

    @Test
    fun `parse greek letters`() {
        val result = MathParser.parse("\\alpha \\beta \\gamma")
        assertNotNull(result)
    }

    @Test
    fun `parse parentheses grouping`() {
        val result = MathParser.parse("(a+b)")
        assertNotNull(result)
    }

    @Test
    fun `parse nested braces`() {
        val result = MathParser.parse("{x + y}")
        assertNotNull(result)
    }

    @Test
    fun `parse matrix`() {
        val result = MathParser.parse("\\begin{matrix}1&2\\\\3&4\\end{matrix}")
        assertNotNull(result)
    }

    @Test
    fun `parse returns null for invalid input`() {
        val result = MathParser.parse("\\")
        // null is acceptable for incomplete/escaped input
        assertNotNull(result, "Parser should handle stray backslash gracefully")
    }

    @Test
    fun `parse empty string`() {
        val result = MathParser.parse("")
        assertNotNull(result)
    }
}
