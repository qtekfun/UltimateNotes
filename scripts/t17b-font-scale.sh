#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateNotes contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Runs FontScaleTest on a connected device with the system font scale at 1.3 and 2.0, restoring
# the original value at the end (also on failure) and printing it so it can be checked:
#   scripts/t17b-font-scale.sh <adb-selector>      e.g.  -s SERIAL   or   -t 130
# The APKs must already be installed (./gradlew assembleDebug assembleDebugAndroidTest, adb install -r).
set -euo pipefail

if [ "$#" -ne 2 ] || { [ "$1" != -s ] && [ "$1" != -t ]; } || [ -z "$2" ]; then
  echo "usage: $0 <adb-selector>   (e.g. -s SERIAL or -t TRANSPORT_ID)" >&2
  exit 2
fi
ADB=(adb "$1" "$2")
PKG=com.qtekfun.ultimatenotes
RUNNER="$PKG.test/$PKG.HiltTestRunner"

ORIGINAL="$("${ADB[@]}" shell settings get system font_scale | tr -d '\r')"
echo "original font_scale: $ORIGINAL"
restore() {
  if [ "$ORIGINAL" = null ]; then
    "${ADB[@]}" shell settings delete system font_scale >/dev/null || true
  else
    "${ADB[@]}" shell settings put system font_scale "$ORIGINAL" >/dev/null || true
  fi
  echo "font_scale now: $("${ADB[@]}" shell settings get system font_scale | tr -d '\r') (was $ORIGINAL)"
}
trap restore EXIT

status=0
for scale in 1.3 2.0; do
  echo "=== font scale $scale"
  "${ADB[@]}" shell settings put system font_scale "$scale"
  sleep 2
  "${ADB[@]}" shell am instrument -w -e class "$PKG.quality.FontScaleTest" \
    -e expectedFontScale "$scale" "$RUNNER" || status=1
done
exit "$status"
