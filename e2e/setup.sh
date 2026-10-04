#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateNotes contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Prepares the throwaway Nextcloud of e2e/docker-compose.yml for the E2E suite: waits until it is
# installed, installs/enables the Notes app, creates an app password and prints the variables
# `./gradlew e2eTest` reads. Use: eval "$(e2e/setup.sh | grep '^export ')"
set -euo pipefail

CONTAINER="${E2E_CONTAINER:-ultimatenotes-e2e-nextcloud}"
URL="${NC_URL:-http://localhost:8080}"
ADMIN_USER="${E2E_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-admin-e2e-only}"
TIMEOUT="${E2E_TIMEOUT:-600}"
ENGINE="${CONTAINER_ENGINE:-docker}"

occ() { "$ENGINE" exec --user www-data "$CONTAINER" php occ "$@"; }

echo "Waiting for Nextcloud (up to ${TIMEOUT}s)..." >&2
deadline=$((SECONDS + TIMEOUT))
until curl -fsS "$URL/status.php" 2>/dev/null | grep -q '"installed":true'; do
  if ((SECONDS > deadline)); then
    echo "Nextcloud did not become ready in ${TIMEOUT}s" >&2
    "$ENGINE" logs --tail 50 "$CONTAINER" >&2 || true
    exit 1
  fi
  sleep 3
done

echo "Installing the Notes app..." >&2
if ! occ app:list | grep -q '^  - notes:'; then
  occ app:install notes >&2
fi
occ app:enable notes >&2

# The client uses an app password in production, so the suite does too. The OCS route returns it
# once, authenticating with the installer's admin password.
APP_PASSWORD="$(curl -fsS -u "$ADMIN_USER:$ADMIN_PASSWORD" -H 'OCS-APIRequest: true' \
  "$URL/ocs/v2.php/core/getapppassword?format=json" |
  sed -n 's/.*"apppassword":"\([^"]*\)".*/\1/p')"
if [ -z "$APP_PASSWORD" ]; then
  echo "Could not create an app password" >&2
  exit 1
fi

# Fail early if the Notes API is not answering.
code="$(curl -s -o /dev/null -w '%{http_code}' -u "$ADMIN_USER:$APP_PASSWORD" \
  "$URL/index.php/apps/notes/api/v1/notes")"
if [ "$code" != 200 ]; then
  echo "Notes API answered HTTP $code" >&2
  exit 1
fi

echo "Ready." >&2
echo "export NC_URL=$URL"
echo "export NC_USER=$ADMIN_USER"
echo "export NC_APP_PASSWORD=$APP_PASSWORD"
