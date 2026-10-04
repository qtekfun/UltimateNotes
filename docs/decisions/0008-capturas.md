<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0008 — Capturas de F-Droid sin cuenta ni servidor

Decisiones tomadas sin supervisión. **Ni el test ni el script se han ejecutado nunca en un dispositivo** (solo compilan). Se deshacen con `git revert` del PR.

| # | Decisión | Motivo |
|---|---|---|
| 1 | `ScreenshotTest` (androidTest, Hilt) abre la `MainActivity` real con notas de demostración en una base Room **en memoria** y una sesión falsa en memoria. | Sin cuenta, servidor, red ni notas reales; la UI es la de producción. |
| 2 | Se desinstalan `DatabaseModule`, `AuthModule` y `SettingsModule` solo en ese test y se sustituyen por `Demo*Module` (en `androidTest/.../screenshots`). Los ajustes van a un fichero propio (`demo_screenshot_settings`). | La app instalada en el móvil (notas, cuenta, ajustes) no se toca. Los módulos `Demo*` están en el nivel superior: un futuro test Hilt tendría que desinstalar también los reales. |
| 3 | WorkManager con `WorkManagerTestInitHelper` (restricciones nunca cumplidas) en vez de reemplazar `SyncModule`. | Nada se sincroniza mientras se captura; evita duplicar el módulo. |
| 4 | Nuevo `HiltTestRunner` (ya citado en `CLAUDE.md`) como runner de todos los androidTest. Dependencias solo de test: `hilt-android-testing` (Apache-2.0, misma versión que Hilt) y `work-testing` (Apache-2.0). Ninguna dependencia de producción. | Hilt necesita `HiltTestApplication`; los tests sin Hilt no se ven afectados. |
| 5 | Idioma con locales por app (`LocaleManager`, Android 13+); se restaura el anterior al terminar. Notas de demo traducidas (`DemoNotes`). | No cambia el idioma del sistema. En Android < 13 el test falla con un mensaje claro. |
| 6 | Las capturas salen con `UiAutomation.takeScreenshot()` (pantalla completa, con barra de estado) a `Android/media/<pkg>/screenshots/<locale>/N_nombre.png`. | Esa ruta es escribible sin permisos y legible con `adb pull`. |
| 7 | Cinco capturas por idioma: `1_list`, `2_folders`, `3_editor`, `4_search`, `5_dark`. Fuera de alcance: widget. Colores dinámicos desactivados y tema fijado, para que no dependan del fondo de pantalla. | Nombres al estilo fastlane (`1_xxx.png`). |
| 8 | `scripts/capture-screenshots.sh -s SERIAL` / `-t ID` (obligatorio, si no, se niega). Fija `ANDROID_SERIAL` para que Gradle instale solo ahí, instala (nunca desinstala), lanza solo `ScreenshotTest`, comprueba `OK (`, y copia a `fastlane/metadata/android/{en-US,es-ES}/images/phoneScreenshots/`. Modo demo del sistema (12:00, batería llena, sin notificaciones) al empezar y salida al final con `trap`, best-effort. | `am instrument` devuelve 0 aunque falle un test. |
| 9 | Las fechas de las notas son relativas al momento de ejecución (hoy, ayer, hace N días): no ejecutar justo tras medianoche. Las PNG generadas no se commitean desde aquí; el mantenedor las revisa y las sube. | Las secciones de la lista dependen de la fecha. |
