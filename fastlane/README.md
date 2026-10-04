<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# F-Droid / fastlane metadata

`metadata/android/{en-US,es-ES}/` holds the store texts that F-Droid reads from the repository: `title.txt`, `short_description.txt` (80 characters at most), `full_description.txt`, and `changelogs/<versionCode>.txt` (500 bytes at most; the file name is the version code, see `RELEASING.md`). Add a new `changelogs/<versionCode>.txt` in both languages for every final release.

`images/icon.png` (en-US) is the launcher icon, drawn from `app/src/main/res/drawable/ic_launcher_foreground.xml` and `values/ic_launcher_background.xml`. It is a simple placeholder (a sheet of paper on blue), not final branding. There is no feature graphic, as in the sister apps.

## Screenshots: still to do

`images/phoneScreenshots/` is intentionally empty: screenshots must be real captures from a device, and none has been taken yet. Before submitting to F-Droid:

1. Install the release APK on a phone and sign in to a test Nextcloud with a few `[test]` notes (never your own notes).
2. Capture 2 to 8 screens per language (notes list, editor with a checklist, folders drawer, search, settings), in the English and in the Spanish UI.
3. Save them as `images/phoneScreenshots/1.png`, `2.png`... in `en-US/` and `es-ES/`, commit them, and add them to the README.
