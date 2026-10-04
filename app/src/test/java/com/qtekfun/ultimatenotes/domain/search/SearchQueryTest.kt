// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchQueryTest {
    private fun terms(input: String) = SearchQuery.parse(input)?.terms

    @Test
    fun `only the last word is a prefix`() {
        assertEquals("\"milk\" \"eg*\"", SearchQuery.parse("milk eg")!!.ftsMatch)
        assertEquals("\"milk*\"", SearchQuery.parse("milk")!!.ftsMatch)
    }

    @Test
    fun `empty, blank and symbol only input has no query`() {
        assertNull(SearchQuery.parse(""))
        assertNull(SearchQuery.parse("  \t\r\n "))
        assertNull(SearchQuery.parse("\"*()-:^"))
        assertNull(SearchQuery.parse("😀"))
    }

    @Test
    fun `whitespace of any kind separates words`() {
        assertEquals(listOf("a", "b", "c"), terms("  a\tb\r\nc  "))
    }

    @Test
    fun `case and accents are folded`() {
        assertEquals(listOf("cafe", "ano"), terms("CAFÉ  Año"))
        // Decomposed accents (e + combining acute) fold the same as the precomposed letter.
        assertEquals(listOf("cafe"), terms("café"))
    }

    @Test
    fun `fts operators and punctuation are plain separators`() {
        assertEquals(listOf("a", "or", "b"), terms("a OR b"))
        assertEquals(listOf("near", "a", "b"), terms("NEAR(a b)"))
        assertEquals(listOf("title", "x"), terms("title:x"))
        assertEquals(listOf("foo", "bar"), terms("\"foo\"-bar*"))
        assertEquals(listOf("don", "t"), terms("don't"))
    }

    @Test
    fun `no double quote can survive into the expression`() {
        val match = SearchQuery.parse("\"he said\" \"\"\" \"x")!!.ftsMatch

        assertEquals("\"he\" \"said\" \"x*\"", match)
    }

    @Test
    fun `digits, other scripts and emoji next to words`() {
        assertEquals(listOf("2026", "東京"), terms("2026 東京"))
        assertEquals(listOf("hola", "mundo"), terms("hola😀mundo"))
        assertEquals(listOf("𝒜"), terms("𝒜"))
    }

    @Test
    fun `extra words and very long words are bounded`() {
        assertEquals(SearchQuery.MAX_TERMS, terms("a b c d e f g h i j k")!!.size)
        assertEquals(SearchQuery.MAX_TERM_LENGTH, terms("x".repeat(500))!!.single().length)
    }

    @Test
    fun `a long word is never cut in the middle of a surrogate pair`() {
        val word = "x".repeat(SearchQuery.MAX_TERM_LENGTH - 1) + "𝒜"

        val term = terms(word)!!.single()

        assertEquals(SearchQuery.MAX_TERM_LENGTH - 1, term.length)
        assertFalse(term.last().isHighSurrogate())
    }

    @Test
    fun `queries compare by their words and never print them`() {
        assertEquals(SearchQuery.parse("Hello  World"), SearchQuery.parse("hello world"))
        assertEquals(SearchQuery.parse("a").hashCode(), SearchQuery.parse("A").hashCode())
        assertNotEquals(SearchQuery.parse("a"), SearchQuery.parse("b"))
        assertFalse(SearchQuery.parse("a")!!.equals("a"))
        assertFalse(SearchQuery.parse("secret").toString().contains("secret"))
    }
}
