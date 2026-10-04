#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateNotes contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Takes the F-Droid phone screenshots on a connected device, with demo notes and no account:
#   scripts/capture-screenshots.sh <adb-selector>      e.g.  -s SERIAL   or   -t 82
#
# The selector is REQUIRED so the script can never touch a device you did not choose. It
# installs the debug and androidTest APKs (an update: nothing is ever uninstalled), runs only
# ScreenshotTest, and pulls the PNGs into fastlane/metadata/android/<locale>/images/phoneScreenshots/.
# The system demo mode (12:00, full battery, no notifications) is on while it runs, and is
# switched off at the end, best-effort. Needs Android 13 or newer on the device.
set -euo pipefail

usage() {
  echo "usage: $0 <adb-selector>   (e.g. -s SERIAL or -t TRANSPORT_ID)" >&2
  exit 2
}

[ "$#" -eq 2 ] || usage
case "$1" in
  -s | -t) ;;
  *) usage ;;
esac
[ -n "$2" ] || usage

ADB=(adb "$1" "$2")
PKG=com.qtekfun.ultimatenotes
RUNNER="$PKG.test/$PKG.HiltTestRunner"
SHOTS="/sdcard/Android/media/$PKG/screenshots"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCALES=(en-US es-ES)

"${ADB[@]}" get-state >/dev/null || { echo "No device for '$1 $2'" >&2; exit 1; }

demo() { # demo <command> [extras...]: one demo-mode broadcast
  "${ADB[@]}" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null 2>&1 || true
}

demo_on() {
  "${ADB[@]}" shell settings put global sysui_demo_allowed 1 || true
  demo enter
  demo clock -e hhmm 1200
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4
  demo network -e mobile show -e datatype none -e level 4
  demo notifications -e visible false
}

demo_off() {
  demo exit
  "${ADB[@]}" shell settings put global sysui_demo_allowed 0 >/dev/null 2>&1 || true
}
trap demo_off EXIT

# Gradle installs on every connected device unless told which one: pin it to the selected one.
ANDROID_SERIAL="$("${ADB[@]}" get-serialno)"
export ANDROID_SERIAL

cd "$ROOT"
./gradlew installDebug installDebugAndroidTest

demo_on
"${ADB[@]}" shell rm -rf "$SHOTS"
log="$(mktemp)"
"${ADB[@]}" shell am instrument -w -e class "$PKG.screenshots.ScreenshotTest" "$RUNNER" | tee "$log"
# `am instrument` exits 0 even when a test fails: look for the success line.
grep -q '^OK (' "$log" || { echo "ScreenshotTest failed, nothing pulled." >&2; rm -f "$log"; exit 1; }
rm -f "$log"

for locale in "${LOCALES[@]}"; do
  dest="fastlane/metadata/android/$locale/images/phoneScreenshots"
  mkdir -p "$dest"
  rm -f "$dest"/*.png
  "${ADB[@]}" pull "$SHOTS/$locale/." "$dest/"
  ls "$dest"
done
echo "Done. Review the PNGs before committing them (fastlane/README.md)."
