<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org).

## [Unreleased]

### Added

- Sign in with Nextcloud's Login Flow v2; the app password is encrypted with the Android Keystore.
- Offline-first sync with Nextcloud Notes (API v1): local database, operation queue with retries and backoff, ETags, periodic sync and pull to refresh. Conflicts never lose text: when both devices change the text (or title) of a note, the other version is kept as a copy; changes to different fields (text, title, folder, favorite) are merged without a copy.
- Notes list grouped by date with previews, favorites on top, folders and subfolders.
- WYSIWYG Markdown editor with a separate title field, formatting bar, autosave, undo and redo; unsupported syntax is kept untouched.
- Interactive checklists.
- Offline full-text search over titles and bodies.
- Home screen widget with recent and favorite notes.
- Biometric lock.
- Export a note as Markdown or PDF.
- Encrypted backup and restore of settings and account.
- Settings: theme (light, dark, pure black, dynamic colors), sort order, sync interval and network, language.
- English and Spanish.
- E2E tests against a real Nextcloud in Docker (`./gradlew e2eTest`) and an optional CI workflow.
- F-Droid metadata: fastlane texts and changelogs in English and Spanish, and the recipe for fdroiddata.
- Project scaffold: Kotlin, Jetpack Compose, Material 3, Hilt, detekt, ktlint, Kover, Android Lint and a CI that uploads the debug APK.
