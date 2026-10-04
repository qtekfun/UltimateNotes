// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import io.gitlab.arturbosch.detekt.Detekt
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverReportFilter
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.licensee)
}

/**
 * The app version lives in one place, `appVersion` in gradle.properties (SemVer, optionally
 * `-rc.N`). The version code is derived from it, so it never depends on dates or the machine:
 * MAJOR.MINOR.PATCH-rc.N -> (MAJOR*10000 + MINOR*100 + PATCH) * 100 + N, and 99 for a final
 * release, which therefore sorts after its release candidates.
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(version)
        ?: error("appVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N: $version")
    val (major, minor, patch, rc) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (rc.isEmpty() || rc.toInt() in 1..98))
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    return base * 100 + (rc.toIntOrNull() ?: 99)
}

/** Release signing from the environment (CI secrets); without it the release APK is unsigned. */
val releaseKeystore: String? = System.getenv("UN_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimatenotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimatenotes"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("UN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UN_KEY_ALIAS")
                keyPassword = System.getenv("UN_KEY_PASSWORD")
            }
        }
    }

    // Reproducible builds (F-Droid): no Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // The git commit is not part of the APK: a build from a source tarball must match.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    sourceSets {
        // Exported Room schemas, read by MigrationTestHelper on the device.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

tasks.withType<Detekt>().configureEach {
    // Match the project bytecode level; detekt defaults to the JDK running Gradle.
    jvmTarget = "17"
}

ktlint {
    version.set(libs.versions.ktlint)
}

// Coverage policy (CLAUDE.md): >= 85% over domain/data/sync, 100% on the conflict resolver, the
// sync queue, the Markdown model converter and the checklist parser. Generated code and pure
// Compose UI are excluded.
val coveredPackages = listOf(
    "com.qtekfun.ultimatenotes.domain",
    "com.qtekfun.ultimatenotes.data",
    "com.qtekfun.ultimatenotes.sync"
)
val criticalPackages = listOf(
    "com.qtekfun.ultimatenotes.sync.queue",
    "com.qtekfun.ultimatenotes.sync.conflict",
    "com.qtekfun.ultimatenotes.domain.markdown",
    "com.qtekfun.ultimatenotes.domain.checklist"
)

/**
 * Generated code and pure Compose UI, excluded from coverage (CLAUDE.md). Applied to each report
 * variant: variant filters replace the global ones instead of adding to them.
 */
fun KoverReportFilter.generatedAndUiCode() {
    packages("com.qtekfun.ultimatenotes.ui", "dagger.hilt.internal", "hilt_aggregated_deps")
    classes(
        "*.R",
        "*.R$*",
        "*.BuildConfig",
        "*Hilt_*",
        "*_HiltModules*",
        "*_Factory",
        "*_Factory$*",
        "*_MembersInjector",
        // Room
        "*_Impl",
        "*_Impl$*",
        // Kotlin compatibility bridges for interface default methods
        "*\$DefaultImpls",
        "*ComposableSingletons*"
    )
    annotatedBy(
        "androidx.compose.ui.tooling.preview.Preview",
        "dagger.Module",
        "dagger.hilt.android.HiltAndroidApp",
        "*Generated*"
    )
}

kover {
    currentProject {
        createVariant("critical") {
            add("debug")
        }
    }

    reports {
        total {
            filters {
                excludes { generatedAndUiCode() }
                includes {
                    packages(coveredPackages)
                }
            }
            verify {
                rule("domain, data and sync") {
                    minBound(85)
                }
            }
        }

        variant("critical") {
            filters {
                excludes { generatedAndUiCode() }
                includes {
                    packages(criticalPackages)
                }
            }
            verify {
                rule("sync queue, conflict resolver, markdown converter and checklist parser") {
                    minBound(100, CoverageUnit.LINE)
                    minBound(100, CoverageUnit.BRANCH)
                }
            }
        }
    }
}

tasks.named("koverVerify") {
    dependsOn("koverVerifyCritical")
}

tasks.named("check") {
    dependsOn("koverVerify")
}

// Only GPL-3.0-compatible free licenses may ship in the APK. Anything else,
// including dependencies without a declared license, fails the build.
// Add other GPL-3.0-compatible SPDX ids (MIT, BSD-2-Clause, BSD-3-Clause,
// ISC...) only when a dependency needs them; licensee warns about unused ones.
licensee {
    allow("Apache-2.0")
    // commonmark-java (Markdown analysis in the domain layer).
    allow("BSD-2-Clause")
    // Glance bundles a repackaged protobuf (glance-appwidget-external-protobuf), BSD-3-Clause.
    allow("BSD-3-Clause")
}

// Google Play Services, Firebase and Crashlytics are banned outright (F-Droid
// rules in CLAUDE.md), regardless of what license they declare.
val checkForbiddenDependencies = tasks.register("checkForbiddenDependencies") {
    group = "verification"
    description =
        "Fails if a runtime classpath contains Google Play Services, Firebase or Crashlytics."
    val forbiddenGroupPrefixes = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.crashlytics",
        "io.fabric"
    )
    val runtimeModules = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").map { name ->
        configurations.named(name).flatMap { it.incoming.resolutionResult.rootComponent }
    }
    doLast {
        val modules = mutableSetOf<String>()
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(runtimeModules.map { it.get() })
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (seen.add(component)) {
                (component.id as? ModuleComponentIdentifier)?.let {
                    modules.add(it.moduleIdentifier.toString())
                }
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .forEach { pending.add(it.selected) }
            }
        }
        val offenders = modules
            .filter { module -> forbiddenGroupPrefixes.any { module.startsWith(it) } }
            .sorted()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Forbidden non-free dependencies found: ${offenders.joinToString()}"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkForbiddenDependencies)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.mockwebserver.junit5)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.gfm.tables)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Migration tests run on a device (MigrationTestHelper); they are compiled but not run by check.
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.sqlite.bundled)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.work.testing)
    // Host JVM build of the bundled SQLite, so Room runs in local unit tests.
    testImplementation(libs.sqlite.bundled.jvm)
}
