// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The guard itself: it must flag bad input, or a green [PolicyFilesTest] means nothing. */
class SecurityPolicyTest {
    private fun manifest(
        appAttrs: String = """android:allowBackup="false"
            android:dataExtractionRules="@xml/a" android:fullBackupContent="@xml/b"
            android:networkSecurityConfig="@xml/n"""",
        body: String = "",
        permissions: String = """<uses-permission android:name="android.permission.INTERNET"/>"""
    ) = """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
        $permissions<application $appAttrs>$body</application></manifest>"""

    private fun List<String>.has(part: String) = any { part in it }

    @Test
    fun `a minimal safe manifest passes`() {
        assertEquals(emptyList<String>(), SecurityPolicy.manifest(manifest()))
    }

    @Test
    fun `manifest flags backup, cleartext and debuggable`() {
        val bad = SecurityPolicy.manifest(
            manifest(
                appAttrs = """android:allowBackup="true" android:usesCleartextTraffic="true"
                    android:debuggable="true""""
            )
        )
        listOf(
            "allowBackup",
            "dataExtractionRules",
            "fullBackupContent",
            "networkSecurityConfig",
            "cleartext",
            "debuggable"
        ).forEach { assertTrue(bad.has(it), "not flagged: $it in $bad") }
    }

    @Test
    fun `manifest flags an unexpected permission`() {
        val bad = SecurityPolicy.manifest(
            manifest(
                permissions = """<uses-permission android:name="android.permission.INTERNET"/>
                    <uses-permission android:name="android.permission.READ_CONTACTS"/>"""
            )
        )
        assertEquals(listOf("permission not allowed: android.permission.READ_CONTACTS"), bad)
    }

    @Test
    fun `manifest flags exported components other than the two allowed`() {
        val ok = """<activity android:name=".ui.MainActivity" android:exported="true"/>
            <receiver android:name=".ui.widget.NotesWidgetReceiver" android:exported="true"/>"""
        assertEquals(emptyList<String>(), SecurityPolicy.manifest(manifest(body = ok)))

        val bad = SecurityPolicy.manifest(
            manifest(body = """<service android:name=".Evil" android:exported="true"/>""")
        )
        assertEquals(listOf("component must not be exported: .Evil"), bad)
    }

    @Test
    fun `manifest flags providers without an explicit exported flag or without URI grants`() {
        val noFlag = SecurityPolicy.manifest(
            manifest(body = """<provider android:name=".P"/>""")
        )
        assertTrue(noFlag.has("explicit exported"))

        val noGrant = SecurityPolicy.manifest(
            manifest(
                body = """<provider android:name="androidx.core.content.FileProvider"
                    android:exported="false"/>"""
            )
        )
        assertTrue(noGrant.has("per-URI"))
    }

    private fun extraction(cloud: String, transfer: String = cloud) =
        """<data-extraction-rules><cloud-backup>$cloud</cloud-backup>
        <device-transfer>$transfer</device-transfer></data-extraction-rules>"""

    private val everything = listOf("root", "file", "database", "sharedpref", "external")
        .joinToString("") { """<exclude domain="$it"/>""" }

    @Test
    fun `backup rules that exclude every domain pass`() {
        assertEquals(emptyList<String>(), SecurityPolicy.backupRules(extraction(everything)))
        assertEquals(
            emptyList<String>(),
            SecurityPolicy.backupRules("<full-backup-content>$everything</full-backup-content>")
        )
    }

    @Test
    fun `backup rules flag a missing exclusion, an include and a missing section`() {
        val missing = SecurityPolicy.backupRules(
            extraction(everything.replace("""<exclude domain="database"/>""", ""))
        )
        assertTrue(missing.has("domain database"))

        val included = SecurityPolicy.backupRules(
            extraction("""$everything<include domain="file" path="x"/>""")
        )
        assertTrue(included.has("includes data"))

        val noSection = SecurityPolicy.backupRules(
            "<data-extraction-rules><cloud-backup>$everything</cloud-backup></data-extraction-rules>"
        )
        assertTrue(noSection.has("missing device-transfer"))

        assertTrue(
            SecurityPolicy.backupRules("<data-extraction-rules/>").has("no backup section")
        )
    }

    @Test
    fun `network config flags cleartext, pins, domain rules and unknown trust anchors`() {
        val safe = """<network-security-config><base-config cleartextTrafficPermitted="false">
            <trust-anchors><certificates src="system"/><certificates src="user"/></trust-anchors>
            </base-config></network-security-config>"""
        assertEquals(emptyList<String>(), SecurityPolicy.networkSecurityConfig(safe))

        val bad = SecurityPolicy.networkSecurityConfig(
            """<network-security-config>
            <base-config cleartextTrafficPermitted="true"><trust-anchors>
            <certificates src="@raw/mine"/></trust-anchors></base-config>
            <domain-config><pin-set/></domain-config></network-security-config>"""
        )
        listOf("permits cleartext", "@raw/mine", "per-domain", "pin-set")
            .forEach { assertTrue(bad.has(it), "not flagged: $it in $bad") }
    }

    @Test
    fun `source scan flags logging and custom TLS`() {
        val bad = SecurityPolicy.sources(
            sequenceOf(
                "A.kt" to "import android.util.Log\nLog.d(\"x\", note)",
                "B.kt" to "println(note)",
                "C.kt" to "client.hostnameVerifier { _, _ -> true }",
                "D.kt" to "object : X509TrustManager {}",
                "E.kt" to "val log = Logger()\nval catalog = 1"
            )
        )
        assertEquals(
            setOf(
                "A.kt: logging",
                "B.kt: console output",
                "C.kt: custom TLS validation",
                "D.kt: custom TLS validation"
            ),
            bad.toSet()
        )
    }

    @Test
    fun `forbidden dependency matching uses group prefixes`() {
        val prefixes = listOf("com.google.firebase", "com.google.android.gms")
        val found = SecurityPolicy.forbiddenDependencies(
            listOf(
                "com.google.firebase:firebase-core",
                "com.google.android.gms:play-services-base",
                "com.google.guava:listenablefuture",
                "androidx.core:core"
            ),
            prefixes
        )
        assertEquals(
            listOf(
                "com.google.android.gms:play-services-base",
                "com.google.firebase:firebase-core"
            ),
            found
        )
    }
}
