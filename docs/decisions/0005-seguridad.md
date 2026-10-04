<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0005 — Revisión de seguridad y privacidad (T19)

Decisiones tomadas sin supervisión durante la auditoría. Todas se pueden deshacer con `git revert`.

| # | Decisión | Motivo |
|---|---|---|
| 1 | HTTP en claro prohibido siempre, sin excepción para `localhost`. `ServerUrl` lo rechaza y `network_security_config` pone `cleartextTrafficPermitted=false`. | Ya era la regla de SPEC §6. Los tests usan el parámetro interno `allowInsecure` con MockWebServer, que no existe en producción. |
| 2 | Se mantiene la confianza en CAs instaladas por el usuario (`src="user"`). | Necesario para Nextcloud autoalojado con CA propia (SPEC §6). Validación de certificado y de nombre de host nunca se desactiva (un test escanea el código buscando `TrustManager`/`HostnameVerifier`). |
| 3 | `BasicAuthInterceptor` solo añade `Authorization` al host de la cuenta; el resto de peticiones salen sin la cabecera. Además `followSslRedirects(false)`. | OkHttp ya quita la cabecera en redirecciones a otro origen; esto lo hace explícito y evita bajar de https a http. |
| 4 | Las URLs con credenciales (`https://usuario:clave@host`) se rechazan como inválidas. | Se guardaban y mostrarían en `toString`, y acabarían en copias o informes. |
| 5 | `toString` de modelos con texto de nota o secretos (entidad, DTOs, resultados de búsqueda, widget, ediciones, login) se redacta. | Un crash o un log accidental no debe filtrar notas ni la contraseña. La app no tiene ningún `Log`/`println` y un test lo vigila. |
| 6 | No se cifra la base de datos Room. | Cifrarla exigiría SQLCipher (binario nativo, otra licencia y otro coste de build) o una clave en Keystore sin ganancia real frente al sandbox de Android. Queda documentado en `PRIVACY.md`. |
| 7 | Se mantienen exportados solo `MainActivity` y el receptor del widget. Un extra de id de nota ajeno solo abre una nota si el id es positivo y existe, y la app sigue detrás del bloqueo. | El lanzador y el host de widgets lo necesitan. El `FileProvider` no está exportado. |
| 8 | Licencias: todo Apache-2.0 salvo BSD-2-Clause (commonmark) y BSD-3-Clause (protobuf reempaquetado de Glance), compatibles con GPLv3. No hay dependencias de Play Services, Firebase ni analíticas (`checkForbiddenDependencies`). Sin anti-features de F-Droid (NonFreeNet/NonFreeDep/Tracking). | Informe `./gradlew :app:licensee` y `dependencies`. |
| 9 | La guardia de CI es un test unitario (`PolicyFilesTest`, con `SecurityPolicyTest` que prueba la propia guardia con entradas malas) más la tarea Gradle `checkForbiddenDependencies`, que lee `config/security/forbidden-dependency-groups.txt`. Ambos corren con `check`. | Sin tarea Gradle extra ni scripts fuera de `check`. |

## Abierto
- No se ha podido comprobar en dispositivo (sin adb) el comportamiento de `FLAG_SECURE` ni del widget bloqueado; solo hay tests unitarios.
