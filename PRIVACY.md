<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Privacy policy

*Español más abajo.*

UltimateNotes is a client for the notes in your own Nextcloud. It has no servers of its own, no accounts of its own, no ads, no analytics, no crash reporting and no telemetry. Nobody but you and your Nextcloud server sees your data. It is free software (GPL-3.0-or-later) and contains no Google Play Services, Firebase or other proprietary components.

## What is stored on your device, and where

| Data | Where | Notes |
|---|---|---|
| Your notes (title, text, folder, favorite flag) | App-private database | Not encrypted at rest by the app; protected by Android's app sandbox and, on most devices, full-disk encryption. |
| Server address and user name | App-private preferences | |
| App password | App-private preferences, encrypted with a key in the Android Keystore | The key never leaves the device. The app only asks Nextcloud for an app password (Login Flow v2); it never sees your main password. |
| Settings (theme, sync interval, lock option) | App-private preferences | |
| Temporary export files (PDF, Markdown) | App cache | Created only when you share a note; deleted automatically after a short time. |

## What leaves your device

- Your notes and your app password travel only between your device and the Nextcloud server you sign in to, over HTTPS. Plain `http://` addresses are refused, and the app password is only ever sent to that server's own address (never to another host, not even after a redirect).
- Nothing is sent anywhere else: no analytics, no crash reports, no advertising identifiers.
- Note content, titles, passwords and passphrases are never written to logs or crash messages.
- If you share or save an exported note, it goes where you choose (the app you pick in Android's share sheet, or the file you save).

## Backups

- Android's automatic cloud backup and device-to-device transfer are disabled: nothing of the app is copied by them.
- The app has its own backup file, which you create and store yourself. It holds your settings and your sign-in, **including the app password**, and is encrypted with the passphrase you choose (PBKDF2-SHA256 and AES-256-GCM). Keep the file and the passphrase safe; anyone who has both can use your Nextcloud account until you revoke that app password in Nextcloud. Your notes are not in the backup; they come back from your server.

## Screen privacy and app lock

- The optional lock (biometrics or device credential) hides the app and its widget content until you unlock it.
- The optional "hide content" setting blocks screenshots and hides the app in the recent-apps view.
- The home-screen widget shows note titles and previews; it shows nothing while the app is locked.

## Permissions

| Permission | Why |
|---|---|
| Internet (`INTERNET`) | To talk to your Nextcloud server. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`) | Lets a sync that you start finish while the app is in the background. |

No storage, contacts, location, camera or microphone permission is requested. If you use a self-hosted server with your own certificate authority, the app trusts certificates installed by the user in Android, as well as the system ones; certificate validation is never turned off.

## Changes and contact

If this policy changes, the change is recorded in the project's history. Questions: open an issue at https://github.com/qtekfun/UltimateNotes.

---

# Política de privacidad

UltimateNotes es un cliente para las notas de tu propio Nextcloud. No tiene servidores propios, ni cuentas propias, ni anuncios, ni analíticas, ni telemetría. Nadie salvo tú y tu servidor Nextcloud ve tus datos.

## Qué datos van adónde

- Tus datos solo viajan entre tu dispositivo y el servidor Nextcloud en el que inicias sesión, siempre por HTTPS.
- La copia de seguridad en la nube de Android está desactivada, así que no copia nada.
- No se envía nada a ningún otro sitio y el contenido de las notas nunca se escribe en los registros.

## Permisos

| Permiso | Motivo |
|---|---|
| Internet (`INTERNET`) | Para hablar con tu servidor Nextcloud. |

Este documento crecerá con las funciones (inicio de sesión, copia local de las notas, bloqueo biométrico, exportación).
