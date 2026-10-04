<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0007 — Calidad en JVM (T17a)

Parte de T17 que no necesita dispositivo. El usuario aprobó el plan sin estar presente. La parte
que sí lo necesita queda como **T17b** en `PLAN.md`.

## 1. Test intermitente del editor: causas y arreglo
`EditorViewModelTest > renaming an existing note saves the title only, after the debounce` fallaba
alguna vez en CI. No era un problema de tiempos de espera: había **dos carreras reales**.

**A. Fuga entre tests (la que explica el fallo en CI).** `viewModelScope` (en `Main`) observa Room.
En `tearDown` se hacía `Dispatchers.resetMain()` y `database.close()` mientras esa corrutina aún se
estaba cancelando. Un hilo de Room la terminaba después, ya sin `Main` (`Method getMainLooper ...
not mocked`), y la excepción salía por el manejador global de hilos: kotlinx-coroutines-test la
atribuye al **siguiente** test (`UncaughtExceptionsBeforeTest`). Por eso fallaba "uno cualquiera"
(el de renombrar casi siempre que otro lo precedía) y solo a veces. Arreglo: `tearDown` cancela y
espera (`cancelAndJoin`) el scope de cada modelo antes de resetear `Main` y cerrar la base.
Los demás tests de ViewModel (`MainViewModelTest`, `SearchViewModelTest`, `AppViewModelTest`) no
usan Room real ni hilos, así que no tienen esta fuga.

**B. Bug de producto al cerrar.** El autosave (tras el debounce) escribe en Room en otro hilo y
`close()` cancela su job. Si la fila ya estaba escrita pero la corrutina no había vuelto de
`writer.update`, la cancelación saltaba antes de anotar `session.wrote` y `lastTitle`; el guardado
final veía "cambiado", `update` devolvía `false` (la base ya tenía ese texto) y **no se pedía la
sincronización**. En la app real la nota esperaba a la siguiente sync periódica. Arreglo:
`persist` corre en `NonCancellable`. Test determinista que reproduce la carrera (`closing while the
autosave is writing still asks for a sync`: retiene el guardado justo después de escribir, cierra y
comprueba la sync; sin el arreglo falla con `expected 1 but was 0`).

Además, `settle()` dormía un tiempo fijo (5 x 10 ms) esperando hilos de Room: ahora espera a que no
quede ninguna corrutina activa en el scope del modelo (el límite de 5 s es solo red de seguridad).
`eventually()` ya esperaba por estado y se mantiene. Ningún otro test usa estos helpers (son
privados de esa clase).

Bucle de verificación, `./gradlew testDebugUnitTest --tests '*EditorViewModelTest*' --rerun`
(clase entera, 31 tests, en un PC con carga media de 30 a 90, que es un entorno hostil):
- solo con el arreglo B: 27/30 pasan (3 fallos, causa A);
- con A y B: **30/30 pasan**.

## 2. Guardas de rendimiento (JVM, etiqueta `perf`)
`app/src/test/.../perf/PerformanceGuardsTest.kt`. Siguen dentro de `check`. Imprimen la mediana
medida (`PERF ...` en el log del test) y fallan solo por encima de un umbral muy holgado, para que
el ruido de la CI no los haga intermitentes. Datos sintéticos deterministas: 5 000 notas de ~2 KB
de Markdown variado (títulos, casillas, citas, negritas) y notas de 50 KB.

| Guarda | Medido (mediana) | Umbral |
|---|---|---|
| Lista: `toListItem` + `buildNoteGroups`, 5 000 notas | MED_LISTA | 2 000 ms |
| Búsqueda FTS en Room en memoria, 5 000 notas (frase de 2 palabras) | MED_BUSQUEDA | 1 000 ms |
| Análisis Markdown + estilos, nota de 50 KB | MED_MD | 1 000 ms |
| Parser de checklist, nota de 50 KB | MED_CHECK | 500 ms |
| Primer pull de 5 000 notas contra `FakeNotesServer` | MED_SYNC | 120 000 ms |

**Aviso sobre las cifras**: se midieron en un PC compartido con otros agentes (carga media de
30 a 90). Son una cota superior; el resultado en reposo es varias veces menor. Los umbrales
dejan más de 5x de margen incluso sobre estas cifras.

Observaciones, sin cambiar código:
- Ningún punto caliente de CPU: lista, búsqueda, Markdown y checklist son de milisegundos o
  decenas de ms.
- El pull inserta nota a nota (una transacción por nota; con los triggers de FTS, ~2 ms por
  inserción en JVM). En un móvil con base en disco cada transacción añade un `fsync`, así que el
  **primer sync de 5 000 notas puede tardar minutos**. No bloquea la UI (es segundo plano), pero
  insertar por lotes (una transacción por página) es la mejora si T17b lo confirma. No se ha
  tocado: el motor de sync es zona de 100 % de cobertura y no hay medida en dispositivo que lo
  justifique.
- Lo que mide el spec (arranque a lista < 1 s) incluye Room, Compose y el dispositivo: eso es T17b.

## 3. Tests instrumentados (solo compilados)
**Nunca se han ejecutado en un dispositivo.** Hay que correrlos cuando el mantenedor tenga uno:
`./gradlew connectedDebugAndroidTest`.

- `EditorTest.tappingACheckboxTogglesExactlyOneCharacterAndUndoRestoresIt`: ya no usa coordenadas
  fijas (fallaba en la tableta). Pide a la semántica del campo su `TextLayoutResult`
  (`SemanticsActions.GetTextLayoutResult`), toma el rectángulo del glifo `[` con
  `getBoundingBox` y le suma el padding (`TEXT_PADDING`, `internal`) del campo. Si
  el campo no expusiera ese layout, el test falla con un mensaje claro y habrá que añadir una
  etiqueta de test o exponerlo de otra forma.
- `AccessibilityTest` (nuevo): la pantalla principal tiene descripción de contenido en el botón de
  sincronizar, el de nota nueva, la barra de búsqueda y la hamburguesa, todos con acción de clic y
  al menos 48 dp de ancho y alto (`assertWidthIsAtLeast` / `assertHeightIsAtLeast`, que miden el
  tamaño de layout; la versión de Compose usada no trae `assertTouch*IsAtLeast`).
- Resto de `androidTest` (`MainContentTest`, `NoteListTest`, `SearchScreenTest`, `LockScreenTest`,
  `PdfExporterTest`, `EditorTest`): igual que antes, compilados y sin ejecutar en esta sesión.

## 4. Pendiente (T17b, necesita dispositivo)
Matriz API 26 / 30 / última; TalkBack (leer en voz alta el orden y las etiquetas); fuente al 200 %;
rotación con el editor abierto; sensación real de fluidez con 5 000 notas (importar con el primer
sync y desplazar la lista); `connectedDebugAndroidTest` completo.
