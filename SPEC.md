# SPEC — UltimateNotes

Estado: borrador v0.1. Los puntos marcados **(DEFECTO)** son decisiones por defecto no confirmadas por el usuario: revisar antes de implementar.

## 1. Visión
Cliente Android de notas para Nextcloud con la experiencia de Apple Notes: carpetas, lista cronológica con previsualización, notas fijadas, checklists y búsqueda. Offline-first. Todo lo que se muestra se puede guardar en Nextcloud Notes; si el servidor no lo soporta, no existe en la app.

## 2. Alcance v1
1. Cuenta Nextcloud (Login Flow v2, contraseña de aplicación en Keystore).
2. Sincronización bidireccional con la API Notes v1.
3. Lista de notas con previsualización, agrupadas por fecha, favoritas arriba.
4. Carpetas (categorías).
5. Editor WYSIWYG que guarda Markdown.
6. Checklists interactivas.
7. Favoritos (= "fijadas").
8. Búsqueda full-text offline.
9. Widget de inicio.
10. Bloqueo con biometría.
11. Exportar nota (md/pdf).
12. Ajustes, tema (dinámico/oscuro/AMOLED), backup cifrado, ES/EN.

## 3. Fuera de alcance (no implementar)
Recordatorios/notificaciones, etiquetas propias, colores de nota, adjuntos e imágenes, bloqueo por nota, notas compartidas/colaboración, integración con Tasks/Deck, tablas y bloques de código con resaltado en el editor (se conservan en el Markdown pero se muestran como texto), dibujo/escaneo, soporte multicuenta, tablet/plegables optimizados, versión iOS/web.

## 4. Modelo de datos (espejo de la API)
`Note { id, etag, readonly, modified (epoch s), title, category, content, favorite }` + campos locales de sync: `localId`, `syncState` (SYNCED / DIRTY / NEW / DELETED / CONFLICT), `lastSyncedEtag`.
- `title` es un campo propio de lectura/escritura (API ≥ 1.0: «título separado, sin renombrado automático según el contenido»). El servidor lo usa también como nombre de archivo: elimina caracteres ilegales (`* | / \ : " < > ?`), recorta a 100 caracteres, añade un número secuencial (`Título (2)`) si ya existe en la misma carpeta, y devuelve el valor saneado, que **el cliente debe adoptar**. Un `POST` sin título (o vacío) crea «New note»; un `PUT` sin `title` lo deja como está, y editar el contenido nunca renombra la nota. Verificado en `docs/api/v1.md` y en `NotesApiController`/`NoteUtil` de nextcloud/notes.
- **Reglas del título en la app**: (1) el usuario lo edita solo en el campo de título del editor (§6); (2) una nota nueva sin título se titula **una sola vez**, al guardarla por primera vez, con su primera línea no vacía (sin `#`/viñetas/casillas, máx. 100 caracteres), o «Nueva nota»/«New note» (localizado) si el cuerpo no tiene texto; desde entonces nunca se vuelve a derivar; (3) las notas descargadas conservan tal cual el título del servidor; (4) un título vacío en una nota existente no se guarda (se conserva el anterior).
- `category` = carpeta; subcarpetas con `/` (ej. `Trabajo/Reuniones`). Vacía = "Sin carpeta".
- Favorita = fijada: único concepto, mapeado a `favorite`.
- Notas `readonly` se muestran sin edición.

## 5. Sincronización
- Endpoint base: `/index.php/apps/notes/api/v1`. Auth Basic con contraseña de aplicación. Cabecera `OCS-APIRequest` no necesaria.
- Descarga: `GET /notes` con `If-None-Match` (ETag de lista) y `pruneBefore` + paginación `chunkSize`/cursor. Borrados detectados por ausencia en la lista completa.
- Subida: `POST /notes`, `PUT /notes/{id}` con `If-Match: <etag>`, `DELETE /notes/{id}`. `POST` y `PUT` envían siempre `title` (omitido solo si está vacío, porque el servidor lo cambiaría por «New note»): con `If-Match` es seguro, y reenviar el título actual no renombra nada. Tras cada subida aceptada la fila adopta el título saneado que devuelve el servidor, salvo que el usuario lo haya editado mientras tanto (entonces sigue `DIRTY` con su título). Un `pull` nunca pisa un título local sin sincronizar (mismas escrituras compare-and-set).
- Cola de sincronización persistida en Room, idempotente y reintentable con backoff (WorkManager). Sync al abrir, al guardar, periódica (configurable) y manual (pull-to-refresh).
- **Conflictos (DEFECTO):** si el servidor responde 412 (etag distinto) y el contenido difiere, se conserva la versión del servidor en la nota original y la versión local se guarda como nueva nota `"<título> (conflicto <fecha>)"` en la misma carpeta. Nunca se pierde texto. Si el contenido **y el título** son idénticos, se adopta el etag nuevo. Un título local no vacío distinto del del servidor cuenta como cambio real aunque el texto coincida: el servidor se queda en la nota original y la local pasa a la copia de conflicto (con su título y texto), de modo que no se pierde ni un título ni un texto.
- Detección de versión: consultar `/api/v1/settings` y la versión de la app Notes; fallar con mensaje claro si la API es < 1.0.
- Errores: sellados (`Offline`, `Unauthorized`, `NotesAppMissing`, `Conflict`, `Server(code)`), nunca excepciones a la UI.

**Decisiones de implementación (T03, T07, T08), verificadas contra `docs/api/v1.md` de nextcloud/notes:**
- Con `chunkSize` el servidor envía las notas podadas (solo `id`) únicamente en el ÚLTIMO trozo: los borrados se infieren solo tras recorrer la lista completa (respuesta sin `X-Notes-Chunk-Cursor`). `pruneBefore` sale de la cabecera `Last-Modified`, no de la hora local. Con `pruneBefore` las notas sin cambios traen solo `id`.
- `chunkSize`/cursor, `If-Match`/etag, `readonly` y `/settings` requieren API 1.2; en servidores antiguos se ignoran los trozos. 400 en `/settings` = `UnsupportedApi`.
- **El servidor compara `If-Match` con el etag entre comillas dobles** (`Helper::getNoteWithETagCheck`): el cliente lo envía entrecomillado (con el etag crudo siempre devolvía 412).
- Errores extra: 403 → `Forbidden` (solo lectura), 404 en una nota → `NotFound` (404 en colección/`/settings` → `NotesAppMissing`), 2xx no JSON → `InvalidResponse`. El cuerpo del 412 trae la nota actual del servidor (`Conflict.serverNote`).
- Conflicto: mismo texto → se adopta el etag nuevo (si difiere carpeta/favorita, sigue `DIRTY` para subirla); texto distinto → el servidor se queda en la nota original y el texto y el título locales pasan a una nota NUEVA con título `"<título local> (conflicto <fecha>)"` (el sufijo va en el título, no en el texto, que se copia intacto; sin título local se usa la primera línea). `SyncState.CONFLICT` nunca se produce: se resuelve al momento.
- PUT con 404 (o nota `DIRTY` ausente de un recorrido completo): la edición local se recrea como nota nueva. Editar gana a borrar en ambos sentidos (DELETE no puede ser condicional). Texto local vacío frente a servidor cambiado: gana el servidor.
- Cada escritura local es compare-and-set: una edición hecha durante un sync nunca se pisa; esa nota se salta (`skipped`) y no se avanza el checkpoint.
- Solo `Offline`, `Unauthorized`, `NotesAppMissing` y `UnsupportedApi` detienen una ejecución; los errores por nota (5xx, 403, 412 sin cuerpo) se cuentan en `SyncReport.skipped`.
- **Workers (T08):** trabajo único `sync-now` (al abrir, tras guardar y pull-to-refresh; `KEEP`, expedited si el sistema lo permite) y periódico único `sync-periodic` (ajuste: desactivado / 15 min / 1 h por defecto / 6 h). Red configurable: cualquiera o solo sin tarifa limitada; el refresco manual usa cualquier red. Se reintenta (`Result.retry`, backoff exponencial desde `BackoffPolicy.initial`, máx. `maxAttempts`) si `skipped > 0` o el error es transitorio; nunca con `Unauthorized`/`NotesAppMissing`/`UnsupportedApi`. El estado (`SyncStatus`: reposo/sincronizando/error(tipo) + última sincronización) es observable y persistente.

## 6. Editor
- WYSIWYG sobre Markdown: el documento en memoria es un modelo de bloques/spans; al guardar se serializa a Markdown.
- Soportado: párrafos, `#`–`###`, negrita, cursiva, tachado, listas con viñetas y numeradas, checklists (`- [ ]`/`- [x]`), citas, enlaces, código en línea.
- Cualquier otra sintaxis se conserva literal (bloque "opaco") y se reserializa sin cambios.
- **Título**: campo de una sola línea sobre el cuerpo (Material 3, estilo de título grande, hint «Título»/«Title»); Intro pasa el foco al cuerpo. No forma parte del Markdown. Se guarda con el mismo autoguardado que el cuerpo y tiene su propio historial de deshacer (los botones de la barra superior deshacen solo el cuerpo). Una nota nueva se crea en cuanto tiene título o texto; si el título lo puso la app (derivado) y el cuerpo se vacía, la nota recién creada se descarta como antes.
- Barra de formato sobre el teclado. Autoguardado con debounce (~1 s) y al salir. Deshacer/rehacer.
- **Decisión T02** (`docs/decisions/0001-editor.md`): un único `BasicTextField(TextFieldState)` cuyo contenido es el Markdown fuente, con estilo en `OutputTransformation`; análisis por rangos (commonmark-java, pendiente de confirmar) en `domain`. `compose-rich-editor` descartada (no lossless, sin checklists/citas). Fallback: modelo de bloques propio.
- Checklists: tocar la casilla alterna `[ ]`/`[x]` también desde la vista de lista (previsualización) sin abrir el editor **(DEFECTO: solo dentro del editor en v1)**.
- **Decisión (1.0):** el editor muestra los símbolos de Markdown (`##`, `**`) atenuados junto al texto con formato; no se ocultan. Es coherente con editar el fuente sin reserializar. Ocultarlos fuera de la línea del cursor (estilo "live preview") se valorará para 1.1 tras uso real.
- **Listas y búsqueda:** las previsualizaciones y los fragmentos de búsqueda muestran texto plano (sin `**`, `#`, `- [ ]`, enlaces ni código); ver `domain/markdown/PlainText`.

## 7. Pantallas
- **Onboarding/Login**: URL del servidor → Login Flow v2.
- **Layout general (calco de Apple Notes, iOS 26)**: barra superior con **menú hamburguesa a la izquierda** (donde Apple pone "Editar") que abre el cajón de carpetas, título de la carpeta actual y acciones a la derecha (selección múltiple, ordenar, más). **Barra de búsqueda inferior** flotante tipo cápsula, siempre visible sobre la lista, con botón de **nota nueva** a su derecha (como Apple). Al enfocar la búsqueda se expande a la pantalla de resultados; el teclado la mantiene pegada encima. Respetar insets (gestos/navegación) y alcance con una mano. Títulos grandes que colapsan al hacer scroll.
- **Carpetas (cajón lateral)**: árbol de categorías con contador, "Todas", "Favoritas", "Sin carpeta", y ajustes al pie.
- **Lista de notas**: secciones *Fijadas*, *Hoy*, *Ayer*, *Últimos 7 días*, *Últimos 30 días*, mes/año. Fila: título guardado, previsualización de 1-2 líneas (el cuerpo sin su primera línea solo cuando esa línea coincide con el título —tolerando el saneado del servidor—; si no, el cuerpo entero), fecha, carpeta. Acciones por swipe (favorito, borrar). Orden por modificación (DEFECTO; configurable: título, fecha).
- **Editor**: campo de título (§6) y cuerpo, barra de formato, menú (favorita, mover a carpeta, exportar, borrar).
- **Búsqueda** (barra inferior, ver Layout): sobre Room FTS (título + contenido), resultados con fragmento resaltado, filtro por carpeta. Decisiones (T12): el índice usa el tokenizador `unicode61` (insensible a mayúsculas y acentos; BD v2 con migración); solo la última palabra de la consulta busca por prefijo; resultados ordenados por coincidencia en el título y luego por fecha de modificación; filtro «todas / esta carpeta» solo dentro de una carpeta; las consultas nunca se registran.
- **Ajustes**: cuenta, sync, tema, orden, bloqueo biométrico, backup, acerca de.
- Borrado: a la papelera no existe en la API → confirmación + "Deshacer" en snackbar durante unos segundos antes de enviar el DELETE.

## 8. Extras
- **Widget (Glance)**: lista de notas recientes/favoritas y botón "Nota nueva". Respeta el bloqueo biométrico (no muestra contenido si está activo).
- **Biometría**: bloqueo global de la app (`BiometricPrompt`, con fallback a credencial del dispositivo), re-bloqueo tras N minutos en segundo plano. `FLAG_SECURE` opcional en ajustes.
- **Exportar**: Markdown (`.md`) y PDF (`PdfDocument`) vía share sheet / SAF. Render PDF desde el modelo del editor.
- **Backup cifrado**: ajustes y cuenta (no las notas, que viven en el servidor). Copiado de UltimateTasks.

## 9. Decisiones técnicas abiertas
- ~~T02: librería/estrategia del editor~~ resuelta, ver §6 y `docs/decisions/0001-editor.md`.
- T03: cliente Retrofit vs OkHttp directo (por defecto Retrofit + kotlinx.serialization como Deck).
- Reutilizar tal cual el módulo de Login Flow v2 de Tasks/Deck.

## 10. No funcionales
- Arranque a lista < 1 s con 5 000 notas (Room + paginación Paging 3 **solo si hace falta**).
- Todo usable offline; la sync nunca bloquea la UI.
- Contenido de notas no se registra en logs; `allowBackup=false` (se usa el backup propio).
- Accesibilidad según CLAUDE.md. Cumple las reglas F-Droid de CLAUDE.md.
- Pruebas de integración contra servidor real solo con notas `[test]`.

## 11. Pruebas (prioridad alta: "se prueba todo muy muy bien")
- Pirámide: unitarias (domain/data/sync, Kover 85% global y 100% en las piezas críticas de CLAUDE.md) → integración (Room en memoria, MockWebServer con respuestas reales de la API Notes, incluidos 304/412/401/404/5xx y paginación) → UI instrumentada en flujos clave (login, crear/editar/borrar, checklist, búsqueda inferior, hamburguesa/carpetas, bloqueo biométrico, exportar).
- Propiedades: tests de ida y vuelta del Markdown con corpus real; test de convergencia de sync (secuencias aleatorias de ediciones offline/online en dos "dispositivos" simulados nunca pierden texto).
- Pruebas manuales/E2E contra servidor Nextcloud real con notas `[test]` antes de cada release (checklist en `RELEASING.md`). CI opcional con Nextcloud en Docker (servicio) para E2E de la API.
- Matriz mínima: API 31 (el mínimo) y la última estable; pantalla pequeña, fuente grande, modo oscuro/AMOLED, ES/EN, TalkBack.
- Ningún release sin `./gradlew check` verde y checklist E2E completa.

- **Plataforma:** `minSdk` 31 (Android 12), decisión del usuario: sin emuladores para API 26/30 y menos ramas de compatibilidad (colores dinámicos, `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, servicio en primer plano `dataSync` y widget con vista previa son siempre válidos).

## 12. Artefactos, firma y publicación
- **Artefactos desde el primer build usable** (T01 en adelante): CI sube el APK debug como artefacto en cada push/PR; desde que exista una pantalla utilizable (hito M1, ver `PLAN.md`) también se publica un **APK firmado de pre-release** (`-rc.N`) en GitHub Releases marcado como pre-release.
- **Versionado y firma idénticos a UltimateDeck/UltimateTasks**: versión en `appVersion` (`gradle.properties`), `versionCode = (MAJOR*10000 + MINOR*100 + PATCH)*100 + N` (`N=99` final), clave propia `ultimatenotes-release.jks`, secretos de GitHub `UN_KEYSTORE_BASE64`, `UN_KEYSTORE_PASSWORD`, `UN_KEY_ALIAS`, `UN_KEY_PASSWORD`. Sin secretos, `assembleRelease` produce APK sin firmar.
- **F-Droid**: build reproducible, `fdroid/com.qtekfun.ultimatenotes.yml` (`Binaries`, `AllowedAPKSigningKeys`, `UpdateCheckMode: Tags` solo versiones finales) y MR a fdroiddata. Los RC no se ofrecen en F-Droid. Cumplir antes las reglas de inclusión (sin antifeatures no declaradas, metadatos fastlane EN/ES, capturas, changelogs por versionCode).

## 13. Criterios de aceptación v1
Crear/editar/borrar/mover/favoritar una nota offline y que converja correctamente al reconectar; un conflicto forzado no pierde texto; ida y vuelta Markdown sin diferencias sobre el corpus; búsqueda offline correcta; `./gradlew check` verde con los umbrales de Kover; APK instalable y metadatos fastlane completos.
