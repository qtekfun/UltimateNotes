# ADR 0001 — Estrategia del editor WYSIWYG sobre Markdown (T02)

- Estado: aceptada · Fecha: 2026-10-04 · Tarea: T02 (alimenta T06 y T11)
- Alcance: solo investigación; no hay código en la app. Datos verificados el 2026-10-04
  (API de GitHub, `docs/` de cada repo, fuentes de androidx-main y Context7).

## Requisitos que deciden
1. Ida y vuelta **sin pérdidas**: lo no soportado (tablas, HTML, notas de otras apps) vuelve idéntico byte a byte.
2. Checklists `- [ ]` / `- [x]` interactivas, citas, `#`–`###`, negrita/cursiva/tachado, enlaces, código en línea.
3. Deshacer/rehacer, IME fiable, minSdk 26, licencia compatible con GPLv3, cero dependencias propietarias/GMS.

## Candidatos evaluados
| Opción | Licencia | Mantenimiento | minSdk / Compose | Ida y vuelta | Checklist |
|---|---|---|---|---|---|
| compose-rich-editor 1.2.0 | Apache-2.0 | Release 2026-09-05, activo | minSdk 23; Compose 1.12 | **No lossless**: serializa desde su modelo; "la sintaxis no soportada se conserva como texto plano" (docs) | **No** (ni citas) en la sintaxis soportada |
| `BasicTextField(TextFieldState)` + `OutputTransformation` | Apache-2.0 (androidx) | Oficial, activo | Compose Foundation (minSdk 23) | **Lossless por construcción** si el texto del estado ES el Markdown | Propio (ver más abajo) |
| Markwon 4.6.2 | Apache-2.0 | Último release 2021-02, último push 2024-04 | Solo Views/Spannable, sin Compose | Solo render, no edición | Solo lectura |
| commonmark-java 0.30.0 | BSD-2-Clause | Release 2026-08-06 | Java 11; "Android best-effort"; sin GMS | Parser con `IncludeSourceSpans`; no reserializa fiel | Ext. `task-list-items`, `gfm-strikethrough` |
| JetBrains/markdown 0.7.9 | Apache-2.0 | Activo (push 2026-09-29) | KMP, pura | Parser con rangos; no reserializa | GFM checkbox |
| Modelo de bloques propio (un `TextField` por bloque) | n/a | Coste nuestro | OK | Lossless con bloques opacos | Fácil (casilla = composable) |

Sobre compose-rich-editor: su `toMarkdown()` parte de `RichTextState` (spans/párrafos), así que cualquier
construcción desconocida se aplana, y no tiene checklists ni citas, necesarios en SPEC §6. La licencia es válida,
pero parchear un serializador no lossless contradice la regla "la sintaxis no soportada nunca se pierde". Descartado.

## Decisión
**Un único `BasicTextField(state: TextFieldState)` cuyo contenido es el Markdown fuente, con estilo visual aplicado
en `OutputTransformation` ("live preview"), y análisis con commonmark-java solo para calcular rangos.**

Razones:
- **Lossless por diseño**: el texto guardado es exactamente el del estado; no hay paso de serialización que pueda
  perder sintaxis. Los "bloques opacos" son rangos a los que simplemente no se da estilo.
- **IME y selección nativas**: un solo campo evita los problemas de selección/foco/teclado entre bloques, que son
  el riesgo principal del modelo "un TextField por bloque".
- **Deshacer/rehacer incluido**: `TextFieldState.undoState` es API pública (androidx-main, `TextFieldState.kt`);
  las ediciones programáticas (`edit {}`) entran en el historial sin fusionarse.
- `OutputTransformation` no altera el estado y documenta `TextFieldBuffer.addStyle` para estilos parciales;
  `replace` dentro de la transformación mantiene el mapeo de offsets, así se puede mostrar `☐`/`☑` en lugar de `- [ ]`.
- Sin dependencias nuevas en la UI; solo commonmark-java (BSD-2) en `domain`, previa confirmación del usuario.

Contrapartidas: los marcadores (`**`, `#`) siguen visibles, atenuados (v1); ocultarlos al estilo Obsidian es posible
después. La transformación se recalcula en cada cambio: en notas largas (>50 KB) habrá que limitar el análisis a las
líneas afectadas (medir en T11/T17).

## Alternativa (fallback)
**Modelo de bloques propio** (`LazyColumn`, un `BasicTextField` por bloque de texto + composable de casilla; bloques
opacos como texto monoespaciado). Se activa si el spike de T11 demuestra que `OutputTransformation` no permite:
(a) casillas tocables fiables, (b) estilos de párrafo por línea (citas, sangría) o (c) rendimiento aceptable con
50 KB. Coste: IME/foco entre bloques. El contrato de T06 (`Segment` + operaciones puras) sigue valiendo.

## Esquema de integración
### T06 — dominio Markdown (`domain.markdown`, sin dependencias de Android)
- `MarkdownAnalyzer.analyze(text): List<Segment>`; `Segment(range, kind, children)` con `kind` ∈ `Heading(level)`,
  `Quote`, `ListItem(bullet|ordered)`, `Checklist(checked, markerRange)`, `Bold|Italic|Strike|Code|Link(urlRange)`,
  `Opaque`. Solo **rangos sobre el texto original**; nunca reserializa.
- Parser: commonmark-java con `IncludeSourceSpans.BLOCKS_AND_INLINES` + `task-list-items` + `gfm-strikethrough`.
  Todo nodo desconocido (tabla, HTML, código vallado, imagen...) → `Opaque`. Parser de checklists propio por línea
  (`^\s*[-*+] \[( |x|X)\] `), 100% de cobertura (CLAUDE.md).
- Operaciones puras `String -> EditResult(text, selection)`: `toggleChecklist`, `toggleInline(Bold|Italic|Strike|Code)`,
  `setBlock(Heading(n)|Quote|List|Checklist)`, `continueList` (Enter en lista/checklist), `deriveTitle`.
- Tests de ida y vuelta sobre corpus real: `analyze` no modifica el texto; aplicar y deshacer una operación = identidad;
  conservar `\r\n` si el original lo usa; fuzz de rangos.

### T11 — UI del editor (`ui.editor`)
- `EditorViewModel` con `TextFieldState` (contenido = Markdown); autoguardado con debounce 1 s vía
  `snapshotFlow { state.text }`; tras cargar la nota, `undoState.clearHistory()`.
- `MarkdownOutputTransformation(segments)`: `addStyle(SpanStyle)` por `Segment`; marcadores con alpha reducida;
  `- [ ]` → `☐`, `- [x]` → `☑` (+ tachado opcional).
- Casilla interactiva: `pointerInput` sobre el campo + `TextLayoutResult` del `onTextLayout` →
  `getOffsetForPosition`; si cae en `markerRange`, `state.edit { ... }` con `toggleChecklist` (edición deshacible).
  Accesibilidad: semántica de checkbox si el spike lo permite.
- Barra de formato sobre el teclado (`imePadding`) llamando a las operaciones puras de T06; Deshacer/Rehacer con
  `undoState.undo()/redo()` y `canUndo/canRedo`.
- Empezar con un spike de medio día que valide (a)(b)(c) del fallback antes de construir el resto.
- Tests: unitarios del dominio (T06); UI Compose solo para escribir, alternar casilla y deshacer.

## Riesgos abiertos
- Versión mínima de Compose Foundation con `addStyle` en `OutputTransformation` (fijarla en T01/T11).
- commonmark-java en Android es "best effort": probar en dispositivo en T11. Sin dependencia, el equivalente es un
  escáner de líneas propio con el mismo contrato `Segment`.
- Dependencia nueva: confirmar con el usuario antes de añadirla (CLAUDE.md).
