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
- [ ] **T05 Persistencia Room**: entidades, DAOs, FTS, migraciones (con tests de migración), tests en memoria.
- [x] **T06 Dominio Markdown**: modelo de bloques, parser/serializador con bloques opacos, parser de checklists, derivación de título. **100% cobertura** + corpus de ida y vuelta.

## Fase 2 — Sincronización
- [ ] **T07 Cola de sync + resolutor de conflictos** (**100% cobertura**): estados, backoff, idempotencia, conflicto → copia local. Test de convergencia con secuencias aleatorias.
- [ ] **T08 Workers**: sync al abrir/guardar/periódica/manual, red restringida configurable.

## Fase 3 — UI (calco Apple Notes)
- [ ] **T09 Shell y tema**: navegación, **cajón de carpetas con menú hamburguesa a la izquierda**, ajustes base, tema dinámico/oscuro/AMOLED, i18n EN/ES (copiar de Tasks).
- [ ] **T10 Lista**: secciones por fecha, favoritas arriba, swipe, deshacer borrado, orden, selección múltiple, títulos grandes colapsables, **barra de búsqueda inferior flotante + botón nota nueva**.
- [ ] **T11 Editor**: integración de T02, barra de formato, checklists, autoguardado, deshacer/rehacer, mover de carpeta, favorita.
- [ ] **T12 Búsqueda (M1)**: FTS offline desde la barra inferior, fragmentos resaltados, filtro por carpeta. **Al cerrar: pre-release `v0.1.0-rc.1` firmada y publicada.**

## Fase 4 — Extras
- [ ] **T13 Biometría**: bloqueo global, timeout, `FLAG_SECURE` opcional.
- [ ] **T14 Exportar**: `.md` y PDF.
- [ ] **T15 Widget Glance**: recientes/favoritas + nota nueva, respeta bloqueo.
- [ ] **T16 Backup cifrado (M2)** (copiar de Tasks).

## Fase 5 — Calidad y cierre
- [ ] **T17 Tests de UI instrumentados** de todos los flujos clave (SPEC §11) en la matriz de dispositivos; accesibilidad (fuente grande, TalkBack) y rendimiento (5 000 notas).
- [ ] **T18 E2E contra Nextcloud real** (notas `[test]`) + job opcional de CI con Nextcloud en Docker; checklist E2E en `RELEASING.md`.
- [ ] **T19 Revisión de seguridad y privacidad**: sin logs de contenido, `allowBackup=false`, escaneo de dependencias (licencias GPLv3), `PRIVACY.md`.
- [ ] **T20 Preparar F-Droid**: metadatos fastlane EN/ES, capturas, changelogs por versionCode, `fdroid/com.qtekfun.ultimatenotes.yml`, README, verificación reproducible local con `fdroid build`.
- [ ] **T21 Release 1.0.0 (M3)**: `-rc.N` necesarios, tag `v1.0.0`, MR a fdroiddata.
