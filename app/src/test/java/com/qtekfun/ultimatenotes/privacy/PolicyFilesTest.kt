// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.privacy

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The CI guard: the real manifest, backup rules, network config and sources obey the policy. */
class PolicyFilesTest {
    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val module = File(checkNotNull(System.getProperty("user.dir")))
    private val main = File(module, "src/main")

    private fun text(path: String) = File(main, path).readText()

    @Test
    fun `manifest obeys the policy`() {
        assertEquals(emptyList<String>(), SecurityPolicy.manifest(text("AndroidManifest.xml")))
    }

    @Test
    fun `both backup rule files exclude everything`() {
        assertEquals(
            emptyList<String>(),
            SecurityPolicy.backupRules(text("res/xml/data_extraction_rules.xml"))
        )
        assertEquals(
            emptyList<String>(),
            SecurityPolicy.backupRules(text("res/xml/backup_rules.xml"))
        )
    }

    @Test
    fun `network security config refuses cleartext`() {
        assertEquals(
            emptyList<String>(),
            SecurityPolicy.networkSecurityConfig(text("res/xml/network_security_config.xml"))
        )
    }

    @Test
    fun `sources neither log nor weaken TLS`() {
        val files = File(main, "java").walkTopDown().filter { it.extension == "kt" }
            .map { it.relativeTo(main).path to it.readText() }
        assertEquals(emptyList<String>(), SecurityPolicy.sources(files))
    }

    @Test
    fun `the forbidden dependency list names the main offenders`() {
        val prefixes = SecurityPolicy.readPrefixes(
            File(module.parentFile, "config/security/forbidden-dependency-groups.txt")
        )
        listOf("com.google.android.gms", "com.google.firebase", "io.fabric", "com.crashlytics")
            .forEach { assertTrue(it in prefixes, "missing $it") }
    }
}
