# UltimateNotes — instrucciones para Claude Code

Cliente Android de notas para **Nextcloud Notes** (API REST), con la experiencia de **Apple Notes**. Offline-first, software libre (GPLv3), destino final: F-Droid. Hermana de [UltimateTasks](https://github.com/qtekfun/UltimateTasks) y [UltimateDeck](https://github.com/qtekfun/UltimateDeck): se copian y adaptan sus piezas comunes.

Lee siempre `SPEC.md` (qué construir) y `PLAN.md` (en qué orden) antes de empezar. Si algo de este archivo contradice a la spec, para y pregunta.

## Identidad del proyecto
- Nombre: **UltimateNotes**
- `applicationId`: `com.qtekfun.ultimatenotes`
- Repositorio: `github.com/qtekfun/UltimateNotes`, rama principal `master`
- Licencia: **GPL-3.0-or-later** (cabecera SPDX en cada archivo fuente)
- Idiomas de la UI: inglés (por defecto) y español. **Ninguna cadena visible va hardcodeada**: todo en `strings.xml` (`values/` y `values-es/`).

## Stack (no cambiar sin preguntar)
- Kotlin, Jetpack Compose, Material 3 (colores dinámicos + modo oscuro + AMOLED)
- `minSdk` 26, `targetSdk` el último estable. Subir `minSdk` solo si algo lo bloquea, y dejarlo anotado en `SPEC.md`.
- Arquitectura: MVVM + capas `ui` / `domain` / `data` / `sync`, flujo de datos unidireccional (StateFlow)
- Inyección: Hilt
- Red: Retrofit + OkHttp + kotlinx.serialization (Nextcloud Notes API v1)
- Persistencia: Room (fuente de verdad única) + FTS para búsqueda
- Segundo plano: WorkManager
- Widget: Jetpack Glance. Biometría: `androidx.biometric`. PDF: `android.graphics.pdf` (sin librerías)
- Gradle con Kotlin DSL y catálogo de versiones (`gradle/libs.versions.toml`)
- Mismas versiones de herramientas que UltimateTasks/UltimateDeck al arrancar (Gradle, AGP, Kotlin, JDK 21 para Gradle, bytecode 17).

## Reutilización de UltimateTasks / UltimateDeck
- Se **copian y adaptan** (no se comparten como librería): Login Flow v2 + Keystore, ajustes y tema, backup cifrado, configuración de calidad/CI, release y receta F-Droid, `.editorconfig`, `.gitignore`, Dependabot.
- Al copiar, se copian también sus tests. Se adaptan nombres de paquete y cadenas; nada de código muerto específico de Tasks/Deck.

## Reglas de software libre (F-Droid) — innegociables
- **Prohibido**: Firebase, Google Play Services, Crashlytics, analíticas, SDKs propietarios, cualquier dependencia no libre.
- **Prohibido** telemetría de ningún tipo.
- Antes de añadir una dependencia: comprueba su licencia (compatible con GPLv3) y pregunta al usuario.
- Metadatos de publicación en formato fastlane: `fastlane/metadata/android/{en-US,es-ES}/`.
- Builds reproducibles: sin timestamps ni valores no deterministas en el build.

## Comandos
- Build debug: `./gradlew assembleDebug`
- Tests unitarios: `./gradlew testDebugUnitTest`
- Tests de UI en el móvil (sin desinstalar la app): `./gradlew installDebug installDebugAndroidTest` y `adb shell am instrument -w com.qtekfun.ultimatenotes.test/com.qtekfun.ultimatenotes.HiltTestRunner`
- Lint y estilo: `./gradlew detekt ktlintCheck lintDebug`
- Cobertura: `./gradlew koverVerify koverHtmlReport`
- Todo lo anterior (lo que corre la CI): `./gradlew check`

## Calidad y tests
- Cada tarea termina con `./gradlew check` en verde. No marques una tarea como hecha si falla.
- Stack de tests: JUnit5 + MockK, Turbine (flows), MockWebServer (API Notes), Room en memoria, tests de UI con Compose solo en flujos clave.
- **Cobertura (Kover):**
  - Umbral global mínimo **85%** sobre `domain`, `data` y `sync`.
  - **100% obligatorio** en: resolutor de conflictos, cola de sincronización, conversor Markdown ↔ modelo del editor y parser de checklists.
  - Excluido de la medición: código generado (Hilt, Room), `@Preview`, UI Compose pura.
  - **Nunca escribas tests vacíos o tautológicos** para subir el número. Un test debe poder fallar por una razón real.
- Markdown: todo lo que el editor lee y escribe pasa por tests de **ida y vuelta** con un corpus de notas reales (Nextcloud Notes web, otras apps). La sintaxis que el editor no soporta se conserva intacta, nunca se pierde.
- Warnings de Kotlin y Lint tratados como errores.

## UI de referencia
- Calco de Apple Notes: **menú hamburguesa a la izquierda** (en vez de "Editar") y **barra de búsqueda inferior flotante** con botón de nota nueva. Detalle en `SPEC.md` §7.

## Release, firma y artefactos
- Idéntico a UltimateDeck/UltimateTasks: `appVersion` en `gradle.properties`, `versionCode` derivado, firma con clave propia vía secretos `UN_KEYSTORE_BASE64`, `UN_KEYSTORE_PASSWORD`, `UN_KEY_ALIAS`, `UN_KEY_PASSWORD`. Nunca commitear el keystore ni las contraseñas.
- CI sube el APK debug como artefacto desde T01; en cuanto haya algo usable (M1) se publican pre-releases `-rc.N` firmadas. Build reproducible obligatorio (F-Droid verifica el APK).
- Se prueba todo: unitarias, integración (MockWebServer), UI instrumentada y E2E contra servidor real (`SPEC.md` §11). Una tarea sin tests que fallen por una razón real no está terminada.

## Flujo de trabajo
- Trabaja **una tarea de `PLAN.md` cada vez**, en una rama `feat/<tarea>`.
- Empieza en modo plan: propón el enfoque y espera confirmación antes de tocar código.
- Commits siguiendo **Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`...), pequeños y atómicos.
- No hagas `git push --force`, no reescribas historia compartida, no toques `master` directamente.
- Al terminar cada tarea: resume en 2-3 líneas qué se hizo y qué queda; marca la tarea en `PLAN.md`.
- Si la spec es ambigua o falta información: **pregunta**, no inventes.
- Pruebas contra el servidor real: usa solo notas/carpetas de prueba creadas para ello (prefijo `[test]`); no modifiques notas del usuario.
- Ahorro de tokens: lee solo los archivos necesarios, usa `rg` antes que leer directorios enteros, no releas lo que acabas de editar y no pegues logs completos (filtra con `tail`/`rg`).

## Convenciones de código
- Un archivo por clase pública relevante; paquetes por feature dentro de cada capa.
- Sin lógica de negocio en composables ni en ViewModels pesados: va en `domain` (Markdown, checklists, ordenación, resolución de conflictos, búsqueda).
- Inmutabilidad por defecto (`val`, `data class`, colecciones inmutables).
- Errores de red/IO modelados con tipos sellados (`Result`/sealed), no con excepciones sueltas hacia la UI.
- Todo el acceso a Room y red fuera del hilo principal (Dispatchers inyectables para poder testear).
- Fechas con `java.time` y un `Clock` inyectable; nunca `System.currentTimeMillis()` directo en lógica testeable.
- Los secretos (contraseñas de aplicación) se guardan cifrados con Android Keystore; nunca en logs ni en texto plano. El contenido de las notas tampoco se loguea jamás.
- Accesibilidad: `contentDescription`, tamaños táctiles mínimos de 48dp, soporte de fuente grande.

## Qué NO hacer
- No implementes nada marcado como "Fuera de alcance" en `SPEC.md`.
- No añadas campos que la API de Nextcloud Notes no pueda guardar (recordatorios, etiquetas propias, adjuntos, colores...).
- No cambies versiones de dependencias manualmente: lo gestiona Dependabot.
- No desactives ni relajes detekt, ktlint, Lint o Kover para que pase la CI.
