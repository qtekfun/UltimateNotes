// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.privacy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

/**
 * The privacy and security rules of the app, checked against the sources by [PolicyFilesTest]
 * (which `check` runs). Each function returns the list of violations, empty when all is well, so
 * the guard itself can be tested with bad input in [SecurityPolicyTest].
 */
object SecurityPolicy {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    val ALLOWED_PERMISSIONS = setOf(
        "android.permission.INTERNET",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
    )

    /** The only components other apps (the launcher, the widget host) may reach. */
    val ALLOWED_EXPORTED = setOf(".ui.MainActivity", ".ui.widget.NotesWidgetReceiver")

    private val BACKUP_DOMAINS = setOf("root", "file", "database", "sharedpref", "external")

    /** Source patterns that would log, or weaken TLS. */
    private val FORBIDDEN_SOURCE = mapOf(
        Regex("""\bandroid\.util\.Log\b|\bLog\.[dviwe]\(|\bTimber\b""") to "logging",
        Regex("""\bprintln\(|\bprintStackTrace\(""") to "console output",
        Regex("""HttpLoggingInterceptor""") to "HTTP logging",
        Regex("""X509TrustManager|HostnameVerifier|hostnameVerifier\b|sslSocketFactory\(""") to
            "custom TLS validation"
    )

    private fun parse(xml: String): Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(InputSource(xml.reader()))
        .documentElement

    private fun NodeList.elements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Element.attr(name: String): String? =
        getAttributeNS(ANDROID, name).takeIf { it.isNotEmpty() }

    fun manifest(xml: String): List<String> {
        val root = parse(xml)
        val violations = mutableListOf<String>()
        val app = root.getElementsByTagName("application").elements().single()

        if (app.attr("allowBackup") != "false") violations += "allowBackup must be false"
        if (app.attr("dataExtractionRules") == null) violations += "dataExtractionRules missing"
        if (app.attr("fullBackupContent") == null) violations += "fullBackupContent missing"
        if (app.attr("networkSecurityConfig") == null) violations += "networkSecurityConfig missing"
        if (app.attr("usesCleartextTraffic") == "true") violations += "cleartext traffic enabled"
        if (app.attr("debuggable") == "true") violations += "debuggable application"

        val permissions = root.getElementsByTagName("uses-permission").elements()
            .mapNotNull { it.attr("name") }
        (permissions - ALLOWED_PERMISSIONS).forEach { violations += "permission not allowed: $it" }

        violations += components(app)
        return violations
    }

    private fun components(app: Element): List<String> {
        val violations = mutableListOf<String>()
        val components = listOf("activity", "service", "receiver", "provider")
            .flatMap { app.getElementsByTagName(it).elements() }
        components.forEach { component ->
            val name = component.attr("name").orEmpty()
            val exported = component.attr("exported")
            when {
                exported == "true" && name !in ALLOWED_EXPORTED ->
                    violations += "component must not be exported: $name"

                // Android 12+ refuses a component with an intent filter and no explicit flag, and
                // a provider without one is a silent risk below that.
                exported == null && component.tagName == "provider" ->
                    violations += "provider without explicit exported: $name"
            }
        }
        components.filter { it.tagName == "provider" && it.attr("exported") != "true" }
            .forEach { provider ->
                if (provider.attr("name") == "androidx.core.content.FileProvider" &&
                    provider.attr("grantUriPermissions") != "true"
                ) {
                    violations += "FileProvider must grant per-URI permissions only"
                }
            }
        return violations
    }

    /** Backup rules (`data_extraction_rules.xml` or `backup_rules.xml`): exclude everything. */
    fun backupRules(xml: String): List<String> {
        val root = parse(xml)
        val sections = if (root.tagName == "data-extraction-rules") {
            root.childNodes.elements()
        } else {
            listOf(root)
        }
        val violations = mutableListOf<String>()
        if (sections.isEmpty()) violations += "no backup section"
        sections.forEach { section ->
            if (section.getElementsByTagName("include").length > 0) {
                violations += "${section.tagName} includes data"
            }
            val excluded = section.getElementsByTagName("exclude").elements()
                .mapNotNull { it.getAttribute("domain") }
                .toSet()
            (BACKUP_DOMAINS - excluded).forEach {
                violations += "${section.tagName} does not exclude domain $it"
            }
        }
        if (root.tagName == "data-extraction-rules") {
            val names = sections.map { it.tagName }
            listOf("cloud-backup", "device-transfer").filter { it !in names }
                .forEach { violations += "missing $it section" }
        }
        return violations
    }

    fun networkSecurityConfig(xml: String): List<String> {
        val root = parse(xml)
        val violations = mutableListOf<String>()
        listOf("base-config", "domain-config", "debug-overrides").forEach { tag ->
            root.getElementsByTagName(tag).elements().forEach {
                if (it.getAttribute("cleartextTrafficPermitted") == "true") {
                    violations += "$tag permits cleartext traffic"
                }
            }
        }
        if (root.getElementsByTagName("domain-config").length > 0) {
            violations += "per-domain trust rules need a documented exception"
        }
        root.getElementsByTagName("certificates").elements()
            .map { it.getAttribute("src") }
            .filter { it != "system" && it != "user" }
            .forEach { violations += "certificates src not allowed: $it" }
        if (root.getElementsByTagName("pin-set").length > 0) {
            violations += "pin-set needs a documented exception"
        }
        return violations
    }

    /** Scans Kotlin sources for logging and custom TLS validation. */
    fun sources(files: Sequence<Pair<String, String>>): List<String> =
        files.flatMap { (path, text) ->
            FORBIDDEN_SOURCE.mapNotNull { (pattern, what) ->
                if (pattern.containsMatchIn(text)) "$path: $what" else null
            }
        }.toList()

    /** [group] ("com.google.firebase") or a module id against the forbidden prefixes. */
    fun forbiddenDependencies(modules: Collection<String>, prefixes: Collection<String>) =
        modules.filter { module -> prefixes.any { module.startsWith(it) } }.sorted()

    fun readPrefixes(file: File): List<String> = file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
}
