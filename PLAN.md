# PLAN — UltimateNotes

Una tarea cada vez, rama `feat/<tarea>`, `./gradlew check` en verde antes de marcarla. Marca `[x]` al terminar.
Hitos: **M0** = CI con artefacto debug (T01) · **M1** = primera versión usable (T12) → primer APK firmado `-rc.1` · **M2** = funcionalidad completa (T16) · **M3** = release 1.0.0 + F-Droid.

## Fase 0 — Base
- [x] **T01 Scaffold + CI de artefactos (M0)**: proyecto Gradle (Kotlin DSL, catálogo, Hilt, Room, Compose, M3), copiar de UltimateTasks: `.editorconfig`, `.gitignore`, detekt/ktlint/Kover/Lint, Dependabot, CHANGELOG, CONTRIBUTING, PRIVACY, RELEASING, LICENSE, fastlane, F-Droid. Cabeceras SPDX. `ci.yml` sube el APK debug como artefacto en cada push/PR.
- [x] **T02 Spike editor**: evaluar librerías WYSIWYG (licencia, ida y vuelta Markdown, rendimiento) vs. editor propio; documentar decisión en `SPEC.md` §6/§9.
- [x] **T03 Cliente API Notes**: Retrofit + serialization, modelos, ETag/If-Match, paginación, errores sellados, tests con MockWebServer (304/412/401/404/5xx).
- [x] **T03b Release pipeline y firma**: `release.yml` y versionado como Deck (`appVersion`, versionCode derivado), clave `ultimatenotes-release.jks`, secretos `UN_*`, comprobación tag = `appVersion`, build reproducible verificado (dos builds → mismo hash). Instrucciones de la clave en `RELEASING.md`.

## Fase 1 — Cuenta y datos
- [x] **T04 Login Flow v2 + Keystore** (copiar de Tasks/Deck con tests), detección de app Notes y versión de API.
- [x] **T05 Persistencia Room**: entidades, DAOs, FTS, migraciones (con tests de migración), tests en memoria.
- [x] **T06 Dominio Markdown**: modelo de bloques, parser/serializador con bloques opacos, parser de checklists, derivación de título. **100% cobertura** + corpus de ida y vuelta.

## Fase 2 — Sincronización
- [x] **T07 Cola de sync + resolutor de conflictos** (**100% cobertura**): estados, backoff, idempotencia, conflicto → copia local. Test de convergencia con secuencias aleatorias.
- [x] **T08 Workers**: sync al abrir/guardar/periódica/manual, red restringida configurable.

## Fase 3 — UI (calco Apple Notes)
- [x] **T09 Shell y tema**: navegación, **cajón de carpetas con menú hamburguesa a la izquierda**, ajustes base, tema dinámico/oscuro/AMOLED, i18n EN/ES (copiar de Tasks).
- [x] **T10 Lista**: secciones por fecha, favoritas arriba, swipe, deshacer borrado, orden, selección múltiple, títulos grandes colapsables, **barra de búsqueda inferior flotante + botón nota nueva**.
- [x] **T11 Editor**: integración de T02, barra de formato, checklists, autoguardado, deshacer/rehacer, mover de carpeta, favorita.
- [x] **T12 Búsqueda (M1)**: FTS offline desde la barra inferior, fragmentos resaltados, filtro por carpeta. **Al cerrar: pre-release `v0.1.0-rc.1` firmada y publicada.**

## Fase 4 — Extras
- [x] **T13 Biometría**: bloqueo global, timeout, `FLAG_SECURE` opcional.
- [x] **T14 Exportar**: `.md` y PDF.
- [x] **T15 Widget Glance**: recientes/favoritas + nota nueva, respeta bloqueo.
- [x] **T16 Backup cifrado (M2)** (copiar de Tasks).

## Fase 5 — Calidad y cierre
- [x] **T17a Calidad en JVM / solo compilación** (hecho): test intermitente del editor estabilizado (carrera real corregida), guardas de rendimiento con 5 000 notas (`perf`), test de la casilla sin coordenadas fijas y test de accesibilidad, ambos compilados pero **nunca ejecutados en dispositivo**. Ver `docs/decisions/0007-calidad-jvm.md`.
- [ ] **T17b Calidad en dispositivo** — **hecho en OPPO CPH2841 / Android 16, quedan 3 cosas** (`docs/decisions/0009-hallazgos-dispositivo.md` y `0010-t17b-dispositivo.md`). Verificado: los tests instrumentados pasan, objetivos táctiles de 48 dp, casilla del editor, capturas de F-Droid, fuente al 130 % y 200 %, rotación (editor, teclado, cajón, búsqueda, ajustes), auditoría de accesibilidad (Accessibility Test Framework, 0 errores), 500/1 000/5 000 notas (pull 2,8 s, búsqueda 55 ms, lista 1,6 s en debug), actualización en sitio, muerte del proceso (bug de restauración arreglado) y log limpio. **Sin verificar**: voz real de TalkBack; fluidez al desplazar con un gesto real y arranque a lista < 1 s en release (1,6 s en debug); biometría. La matriz API 26 / 30 queda descartada (minSdk 31).
- [x] **T18 E2E contra Nextcloud real** (notas `[test]`) + job opcional de CI con Nextcloud en Docker; checklist E2E en `RELEASING.md`. Hecho: `e2e/` (compose + `setup.sh`), `./gradlew e2eTest` (20 tests, ejecutados contra Nextcloud 33.0.9 en Docker local), workflow `e2e.yml`, `docs/decisions/0004-e2e.md`. Queda manual en dispositivo: login flow, UI, offline real en avión, biometría, widget, PDF, backup, actualización sobre versión previa.
- [x] **T19 Revisión de seguridad y privacidad**: sin logs de contenido, `allowBackup=false`, escaneo de dependencias (licencias GPLv3), `PRIVACY.md`.
- [x] **T20 Preparar F-Droid**: metadatos fastlane EN/ES (textos, changelogs `10001` y `1000099`, icono), `fdroid/com.qtekfun.ultimatenotes.yml` (`fdroid readmeta`/`lint` OK con valores ficticios), README, `RELEASING.md` con los pasos del MR a fdroiddata, verificación reproducible local. Decisiones en `docs/decisions/0006-fdroid.md`. Pendiente solo del mantenedor: crear la clave y los secretos `UN_*`, capturas de pantalla en dispositivo (`fastlane/README.md`), rellenar `commit` y `AllowedAPKSigningKeys`, tag y MR a fdroiddata; `fdroid build` real queda para T21.
- [ ] **T21 Release 1.0.0 (M3)**: `-rc.N` necesarios, tag `v1.0.0`, MR a fdroiddata.
