<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Releasing

## Versions

- The version lives in one place: `appVersion` in `gradle.properties`, as SemVer (`1.2.3`), or `1.2.3-rc.N` for a release candidate.
- The Android version code is derived from it, never set by hand: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, with `N = 99` for a final release. So `1.0.0-rc.1` is `1000001` and `1.0.0` is `1000099`: a final version always sorts after its release candidates, and nothing depends on dates or the machine (reproducible builds).
- Before 1.0.0 the app is `0.x`.

## Signing (one time)

Releases are signed with the project's own key, and the builds are reproducible: F-Droid builds the same source, checks that its APK matches the one published here and then ships ours. That way the app can be updated from F-Droid or GitHub interchangeably.

1. Create the key, and keep the file and passwords somewhere safe and **backed up**: if the key is lost, users would have to uninstall to update. Never commit the keystore or the passwords.
   ```sh
   keytool -genkeypair -v -keystore ultimatenotes-release.jks -alias ultimatenotes \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these secrets to the GitHub repository (Settings → Secrets and variables → Actions):
   - `UN_KEYSTORE_BASE64`: `base64 -w0 ultimatenotes-release.jks`
   - `UN_KEYSTORE_PASSWORD`, `UN_KEY_ALIAS` (`ultimatenotes`), `UN_KEY_PASSWORD`
3. For F-Droid, give them the certificate fingerprint (`AllowedAPKSigningKeys` in its metadata):
   ```sh
   keytool -list -v -keystore ultimatenotes-release.jks -alias ultimatenotes | grep SHA256
   ```
   F-Droid wants it in lowercase hex without colons: `... | grep SHA256 | cut -d: -f2- | tr -d ': ' | tr A-F a-f`.

Without these variables, `./gradlew assembleRelease` builds an unsigned APK, which is what F-Droid does before comparing. To sign a build locally, export `UN_KEYSTORE_FILE` (path to the `.jks`), `UN_KEYSTORE_PASSWORD`, `UN_KEY_ALIAS` and `UN_KEY_PASSWORD` first.

## Reproducibility

`./scripts/verify-reproducible.sh` builds the unsigned release APK twice from a clean state, without Gradle caches, and fails if the SHA-256 hashes differ. The Release workflow runs it before building the signed APK, so a non-reproducible build never gets published. Run it locally after touching the build (new plugins, resources, dependencies).

## Making a release

1. Move the `[Unreleased]` notes in `CHANGELOG.md` under `## [X.Y.Z] - YYYY-MM-DD`.
2. Set `appVersion=X.Y.Z` in `gradle.properties`.
3. Run the E2E checklist below against a real Nextcloud (final releases and release candidates alike).
4. Commit (`chore: release X.Y.Z`), merge to `master`, then tag and push the tag:
   ```sh
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```
5. The **Release** workflow checks that the tag matches `appVersion`, verifies reproducibility, runs `./gradlew check`, builds the signed APK and publishes a GitHub Release with the notes of that version from `CHANGELOG.md`. Release candidates (`-rc.N`, with a matching `## [X.Y.Z-rc.N]` section) are marked as pre-releases.
6. F-Droid picks the new tag up by itself (`UpdateCheckMode: Tags`, final versions only: release candidates are not offered there).

## E2E automated (real Nextcloud in Docker)

`./gradlew e2eTest` drives the real `NotesClient` and sync engine (in-memory Room, two simulated clients) against a throwaway Nextcloud with the Notes app. Run it before every release; the **E2E** workflow also runs it nightly and on PRs that touch `data/` or `sync/` (not required for merge).

```sh
docker compose -f e2e/docker-compose.yml up -d
eval "$(e2e/setup.sh | grep '^export ')"   # waits, installs Notes, creates an app password
./gradlew e2eTest
docker compose -f e2e/docker-compose.yml down -v
```

Without `NC_URL`, `NC_USER` and `NC_APP_PASSWORD` the tests are skipped. A server that is not localhost is refused unless `E2E_ALLOW_REMOTE=1`; everything the suite creates is named `[test]...` and deleted afterwards.

Automated: create/edit/delete, title sanitizing and `(2)` numbering, rename, move to folders and subfolders, favorite, list ETag 304, `If-Match` and 412, chunked pagination and deletion inference, conflict copy without losing text, offline-to-online convergence of two clients, Unicode/emoji/CRLF/Markdown round trip, wrong password.

## E2E manual checklist (real device, real Nextcloud)

The sync items above are covered by `e2eTest`; what remains needs the app on a device (login, UI, system features) or a server of your own.

Use the signed APK and a real Nextcloud with the Notes app. Use only a dedicated test folder and notes whose title starts with `[test]`; never touch the user's own notes. Delete the test notes afterwards.

- [ ] Log in with Login Flow v2 on a server with Notes installed; the account appears and the notes list syncs.
- [ ] Log in on a server without the Notes app: a clear error is shown.
- [ ] Create `[test] offline` in airplane mode; reconnect; it appears in the web UI with the same text.
- [ ] Edit a `[test]` note in the app and in the web UI at the same time (one offline); after syncing no text is lost and the conflict leaves a local copy. (Engine side automated; check it on the device, in airplane mode.)
- [ ] Rename, move to another folder, favorite and delete `[test]` notes; each change reaches the server and a second client.
- [ ] A checklist `[test] checklist` toggles items, and the Markdown on the server stays valid (`- [ ]` / `- [x]`).
- [ ] A note with syntax the editor does not support (tables, HTML) is saved unchanged after an unrelated edit.
- [ ] Offline search finds `[test]` notes by title and body.
- [ ] Sync on open, on save, periodic and manual all work; the restricted-network setting is respected.
- [ ] Revoke the app password in Nextcloud: the app asks to log in again without losing local notes.
- [ ] Biometric lock, widget, PDF export and encrypted backup/restore work on the `[test]` notes.
- [ ] Install over the previous release: the app updates in place, data intact (same signing key and a higher version code).

## F-Droid

`fdroid/com.qtekfun.ultimatenotes.yml` is the app's metadata as submitted to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) (`metadata/com.qtekfun.ultimatenotes.yml`). It has no comments because fdroiddata's tools remove them. F-Droid builds each tagged version with JDK 21, like CI, checks that its APK matches ours (`Binaries`, `AllowedAPKSigningKeys`) and then publishes ours. The app declares no `AntiFeatures`: it only talks to the user's own server, so `NonFreeNet` does not apply.

Two fields are placeholders (`TODO-...`) until the key and the first tag exist:

- `commit`: the full 40-character hash of the commit the tag points to (`git rev-parse vX.Y.Z^{commit}`).
- `AllowedAPKSigningKeys`: the certificate fingerprint from [Signing](#signing-one-time), step 3.

`versionName`, `versionCode`, `CurrentVersion` and `CurrentVersionCode` are already filled for 1.0.0 (`1000099`); for another first version use `appVersion` and the formula in [Versions](#versions). After the first inclusion, `AutoUpdateMode: Version` adds the new `Builds` entries by itself for each final tag.

### Submitting the merge request (first inclusion)

Only after the tag, the GitHub Release with the signed APK (`UltimateNotes-X.Y.Z.apk`) and the screenshots exist.

1. Fork <https://gitlab.com/fdroid/fdroiddata> and clone your fork; create a branch `com.qtekfun.ultimatenotes`.
2. Copy `fdroid/com.qtekfun.ultimatenotes.yml` to `metadata/com.qtekfun.ultimatenotes.yml` and fill in `commit` and `AllowedAPKSigningKeys`.
3. Check it, from the fdroiddata checkout (with [fdroidserver](https://gitlab.com/fdroid/fdroidserver) installed, or its Docker image `registry.gitlab.com/fdroid/docker-executable-fdroidserver`):
   ```sh
   fdroid readmeta
   fdroid lint com.qtekfun.ultimatenotes
   fdroid rewritemeta com.qtekfun.ultimatenotes   # must leave no diff
   fdroid build -v -l com.qtekfun.ultimatenotes   # needs the build server setup; optional locally
   ```
4. Commit (`New app: UltimateNotes`) and push the branch to your fork.
5. Open a merge request to `fdroid/fdroiddata` with the project's MR template filled in (checklist included) and link the repository and the release.
6. Answer the reviewers; the pipeline (lint, build, binaries verification) must be green.

### Only the maintainer can do

- [ ] Create the signing key and back it up (see [Signing](#signing-one-time)).
- [ ] Add the secrets `UN_KEYSTORE_BASE64`, `UN_KEYSTORE_PASSWORD`, `UN_KEY_ALIAS` and `UN_KEY_PASSWORD` to the GitHub repository.
- [ ] Capture phone screenshots on a device (English and Spanish UI, 2 to 8 each) into `fastlane/metadata/android/{en-US,es-ES}/images/phoneScreenshots/` (`1.png`, `2.png`...), using only `[test]` notes. See `fastlane/README.md`.
- [ ] Run the manual E2E checklist on a device.
- [ ] Merge the release commit, then create and push the tag `vX.Y.Z` (the Release workflow does the rest).
- [ ] Fill in `commit` and `AllowedAPKSigningKeys` in the yml, and open the fdroiddata merge request.
- [ ] When F-Droid publishes the app, add its badge and link to `README.md`.
