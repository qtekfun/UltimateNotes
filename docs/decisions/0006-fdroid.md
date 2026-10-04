<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0006 — Preparar F-Droid (T20)

Decisiones tomadas sin supervisión. Se deshacen con `git revert` del PR.

| # | Decisión | Motivo |
|---|---|---|
| 1 | Sin `AntiFeatures` (ni `NonFreeNet`). | UltimateTasks y UltimateDeck tampoco declaran ninguna: la app solo habla con el servidor Nextcloud del propio usuario, que es un servicio libre que él elige. |
| 2 | `Categories: Writing`. | Es la categoría de F-Droid para apps de notas (Tasks y Deck usan `Task`). Con `fdroid lint` y un `config/categories.yml` que la define, pasa; sin config da "not valid", es un artefacto del entorno de prueba vacío. |
| 3 | El yml viene rellenado para 1.0.0 (`1000099`) con `commit` y `AllowedAPKSigningKeys` como `TODO-...`. | No existe aún tag ni clave; `fdroid lint` los rechaza a propósito hasta que se rellenen (comprobado con valores ficticios en una copia). Los pasos están en `RELEASING.md`. |
| 4 | Changelogs para `10001` (0.1.0-rc.1) y `1000099` (1.0.0), mismo texto; ninguno para `1000001`. | Fórmula del proyecto. Los RC no se ofrecen en F-Droid, así que solo importa el de la final; el de 10001 cubre el primer `-rc` previsto. Hay que añadir uno por cada versión final. |
| 5 | `icon.png` 512×512 generado desde el icono adaptable actual (papel blanco sobre azul `#0B63CE`). Sin feature graphic. | Los hermanos solo llevan `icon.png` y capturas. El icono de la app es provisional; no se inventa más marca. |
| 6 | Sin capturas: `phoneScreenshots/` vacío y `fastlane/README.md` lo explica. | No se fabrican capturas; hay que hacerlas en un dispositivo (tarea del mantenedor). |
| 7 | Descripción sin promesas fuera de alcance, con aviso de que requiere la app Notes de Nextcloud. | SPEC §3. |
| 8 | `Binaries` apunta a `UltimateNotes-%v.apk` de la GitHub Release, `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$`, `AutoUpdateMode: Version`. | Igual que los hermanos; excluye los `-rc.N`. |
| 9 | Verificación: `fdroid readmeta` y `fdroid lint` con la imagen Docker `docker-executable-fdroidserver`. `fdroid build` no se ejecutó (necesita buildserver/tag). | Honestidad sobre lo comprobado; `verify-reproducible.sh` cubre la reproducibilidad local. |
| 10 | `PRIVACY.md` no se toca aquí (lo lleva T19). | Su texto "esto crecerá con las funciones" quedará obsoleto; conviene que T19 lo actualice antes de la 1.0. |
