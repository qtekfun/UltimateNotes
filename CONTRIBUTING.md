<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Contributing

Thanks for helping! A few rules keep UltimateNotes free, reliable and easy to review.

## Ground rules

- **Free software only.** No Google Play Services, Firebase, analytics, crash reporters or any non-free dependency. Before adding a dependency, open an issue: its license must be compatible with GPL-3.0-or-later.
- **No telemetry**, of any kind.
- **Only what the Nextcloud Notes API can store.** Features it cannot keep (reminders, own tags, attachments, colors…) are out of scope; see SPEC.md.
- **Every visible string in `strings.xml`**, in English (`values/`) and Spanish (`values-es/`).
- **SPDX header** in every source file: `SPDX-License-Identifier: GPL-3.0-or-later`.

## Workflow

1. One task or fix per branch (`feat/…`, `fix/…`), started from `master`.
2. Small, atomic commits following [Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`…).
3. `./gradlew check` must pass before you push: unit tests, detekt, ktlint, Android Lint (warnings are errors) and Kover.
4. Open a pull request against `master`; CI runs the same checks and uploads the debug APK.

## Code

- Kotlin, Jetpack Compose and Material 3; MVVM with `ui` / `domain` / `data` / `sync` layers and unidirectional data flow.
- Room is the single source of truth: the UI reads Room, never the network.
- Network and IO errors are typed results (sealed classes), not exceptions reaching the UI.
- Never log note content or credentials.
