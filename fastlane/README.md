<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# F-Droid / fastlane metadata

`metadata/android/{en-US,es-ES}/` holds the store texts that F-Droid reads from the repository: `title.txt`, `short_description.txt` (80 characters at most), `full_description.txt`, and `changelogs/<versionCode>.txt` (500 bytes at most; the file name is the version code, see `RELEASING.md`). Add a new `changelogs/<versionCode>.txt` in both languages for every final release.

`images/icon.png` (en-US) is the launcher icon, drawn from `app/src/main/res/drawable/ic_launcher_foreground.xml` and `values/ic_launcher_background.xml`. It is a simple placeholder (a sheet of paper on blue), not final branding. There is no feature graphic, as in the sister apps.

## Screenshots

`images/phoneScreenshots/` is filled by a script, with made-up demo notes and no account or server (`docs/decisions/0008-capturas.md`). Connect a phone (Android 13 or newer; the app is updated, never uninstalled, and its own notes and settings are not touched), then run, with the adb selector of that phone:

```sh
scripts/capture-screenshots.sh -s SERIAL   # or: -t TRANSPORT_ID
```

It runs `ScreenshotTest` in English and Spanish and writes `1_list.png`, `2_folders.png`, `3_editor.png`, `4_search.png` and `5_dark.png` into `en-US/` and `es-ES/`. The system demo mode is on while it runs, so the status bar shows 12:00 and a full battery. Look at every PNG before committing it, and add them to the README.
