#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateNotes contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the unsigned release APK twice from a clean state, with no Gradle build cache, and fails
# if the two APKs differ. Run from anywhere: ./scripts/verify-reproducible.sh
set -euo pipefail

cd "$(dirname "$0")/.."
unset UN_KEYSTORE_FILE UN_KEYSTORE_PASSWORD UN_KEY_ALIAS UN_KEY_PASSWORD

apk=app/build/outputs/apk/release/app-release-unsigned.apk

build() {
  ./gradlew --no-build-cache --no-configuration-cache clean assembleRelease -q >&2
  test -f "$apk" || { echo "Missing $apk" >&2; exit 1; }
  sha256sum "$apk" | cut -d' ' -f1
}

first=$(build)
second=$(build)
echo "build 1: $first"
echo "build 2: $second"
if [ "$first" != "$second" ]; then
  echo "The release APK is NOT reproducible." >&2
  exit 1
fi
echo "The release APK is reproducible."
