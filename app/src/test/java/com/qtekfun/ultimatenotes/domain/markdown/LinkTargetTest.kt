// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class LinkTargetTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://example.org",
            "http://example.org/path?q=1#frag",
            "https://example.org:8443/a",
            "https://[2001:db8::1]/x",
            "https://user.example.org/a(b)c",
            "https://example.org/wiki/Foo_(bar)",
            "https://münchen.example/ü?x=✓",
            "https://例え.jp/パス",
            "https://example.org/😀",
            "https://example.org/%3A%2F%2F",
            "https://example.org?x=javascript:alert(1)",
            "https://example.org#a:b",
            "mailto:me@example.org",
            "mailto:me@example.org?subject=Hi%20there",
            "tel:+34600123456",
            "tel:600-123-456;ext=1"
        ]
    )
    fun `allowed destinations come back unchanged`(destination: String) {
        assertEquals(destination, LinkTarget.openable(destination))
    }

    @ParameterizedTest
    @CsvSource(
        "HTTPS://Example.org/Path, https://Example.org/Path",
        "Http://example.org, http://example.org",
        "MAILTO:Me@Example.org, mailto:Me@Example.org",
        "TeL:+34600123456, tel:+34600123456",
        "'  https://example.org  ', https://example.org"
    )
    fun `scheme is lower-cased and surrounding whitespace is trimmed`(
        input: String,
        expected: String
    ) {
        assertEquals(expected, LinkTarget.openable(input))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "intent://scan/#Intent;scheme=zxing;end",
            "intent:#Intent;action=android.intent.action.VIEW;end",
            "file:///sdcard/secret.txt",
            "content://com.android.contacts/contacts",
            "data:text/html;base64,PHNjcmlwdD4=",
            "market://details?id=com.example",
            "android-app://com.example/https/example.org",
            "ftp://example.org/file",
            "sms:+34600123456",
            "geo:40.4,-3.7",
            "vbscript:msgbox(1)",
            "about:blank",
            "blob:https://example.org/uuid",
            "view-source:https://example.org",
            "x-foo+bar.baz:thing",
            "git+ssh://example.org/repo"
        ]
    )
    fun `every other scheme is refused`(destination: String) {
        assertNull(LinkTarget.openable(destination))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "\n",
            "//example.org/path",
            "/relative/path",
            "relative/path.md",
            "./note.md",
            "../note.md",
            "#anchor",
            "?query=1",
            "www.example.org",
            "example.org",
            "1http://example.org",
            "-https://example.org",
            ":https",
            "://example.org",
            "javascript%3Aalert(1)",
            "https%3A//example.org",
            "javascript&colon;alert(1)",
            "\\\\server\\share"
        ]
    )
    fun `destinations without a scheme are refused`(destination: String) {
        assertNull(LinkTarget.openable(destination))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "java\nscript:alert(1)",
            "java\tscript:alert(1)",
            "java\rscript:alert(1)",
            "java script:alert(1)",
            "jav\u0000ascript:alert(1)",
            "javascript\n:alert(1)",
            "\u0000https://example.org",
            "https://example.org\u0000",
            "https://exa\nmple.org",
            "https://exa mple.org",
            "https://example.org/a b",
            "https://example.org/\u0085",
            "https://example.org/\u007f",
            "https://example.org/\u009f",
            "https://example.org/\u00a0x",
            "https://example.org/\u3000x",
            "https://\u200bexample.org",
            "https://example.org/\u202eevil",
            "\u200bhttps://example.org"
        ]
    )
    fun `whitespace, control and invisible characters are refused`(destination: String) {
        assertNull(LinkTarget.openable(destination))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https:",
            "http:",
            "https:example.org",
            "https:/example.org",
            "https:///path",
            "https://",
            "https://?q=1",
            "https://#x",
            "https://:443/path",
            "https://user@example.org",
            "https://user:pass@example.org",
            "https://example.org@evil.example",
            "mailto:",
            "tel:"
        ]
    )
    fun `malformed or credential-bearing web links and empty mail or phone links are refused`(
        destination: String
    ) {
        assertNull(LinkTarget.openable(destination))
    }

    @Test
    fun `tabs and newlines around a link are trimmed`() {
        assertEquals("https://example.org", LinkTarget.openable("\t\r\nhttps://example.org\n\t"))
    }

    @Test
    fun `an at sign outside the authority is allowed`() {
        assertEquals("https://example.org/a@b", LinkTarget.openable("https://example.org/a@b"))
        assertEquals("https://example.org?u=a@b", LinkTarget.openable("https://example.org?u=a@b"))
        assertEquals("https://example.org#@", LinkTarget.openable("https://example.org#@"))
    }

    @Test
    fun `a destination at the length limit is allowed and one over it is not`() {
        val prefix = "https://example.org/"
        val atLimit = prefix + "a".repeat(LinkTarget.MAX_LENGTH - prefix.length)
        assertEquals(atLimit, LinkTarget.openable(atLimit))
        assertNull(LinkTarget.openable(atLimit + "a"))
        assertNull(LinkTarget.openable(prefix + "a".repeat(100_000)))
    }

    @Test
    fun `the length limit applies after trimming`() {
        val prefix = "https://example.org/"
        val atLimit = prefix + "a".repeat(LinkTarget.MAX_LENGTH - prefix.length)
        assertEquals(atLimit, LinkTarget.openable("   $atLimit   "))
    }

    @Test
    fun `parentheses and colons inside an allowed link do not change the verdict`() {
        assertEquals(
            "https://example.org/a(b(c))",
            LinkTarget.openable("https://example.org/a(b(c))")
        )
        assertNull(LinkTarget.openable("(https://example.org)"))
        assertNull(LinkTarget.openable("javascript:(function(){})()"))
    }
}
