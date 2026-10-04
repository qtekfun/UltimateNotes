<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0013 — Abrir enlaces desde el editor

**Decisión (usuario):** opciones A + C.

- **A. Botón contextual.** Con el cursor (selección colapsada) dentro de un enlace, la barra de
  formato muestra «Abrir enlace» / "Open link" (botón de texto: el conjunto de iconos del proyecto,
  `material-icons-core`, no tiene uno libre para esto; alto mínimo 48 dp y `contentDescription`).
  **Tocar un enlace en una nota editable sigue colocando el cursor**, para poder editar el texto.
- **C. Notas de solo lectura** (`readonly`): un toque corto sobre el texto de un enlace lo abre.

Ambos caminos abren a través de un único `LinkOpener` (`domain/link`). La implementación de Android
(`ui/editor/AndroidLinkOpener`) lanza `Intent.ACTION_VIEW` + `CATEGORY_BROWSABLE`; si salta
`ActivityNotFoundException` muestra un `Toast` («No app can open this link» / «Ninguna aplicación
puede abrir este enlace»), sin bloquear.

## Qué cuenta como enlace

Lo que el analizador (`MarkdownAnalyzer`, commonmark) marca como `SegmentKind.Link`: `[texto](url)`,
enlaces por referencia con definición (`[texto][id]`) y autoenlaces `<https://…>`. **No** se
cubren las URL desnudas en el texto (`https://ejemplo.org` sin más) ni las imágenes; ampliarlo
exigiría un detector propio y se deja para más adelante. Con negrita/cursiva dentro del texto del
enlace sigue contando como enlace; con dos enlaces pegados gana el que empieza en el cursor.

Posiciones (`NoteLinks`, en `domain/markdown`): para el cursor, desde el inicio del enlace hasta
justo después de él, ambos incluidos; para un toque (carácter tocado), el rango semiabierto. La
conversión de la posición del puntero a un índice de carácter (`TextLayoutResult`) vive solo en
la capa de UI (`ReadOnlyLinkTaps`).

## Seguridad: `LinkTarget.openable(destino): String?`

El destino sale de una nota, que puede venir de otra persona. Función pura, lista de permitidos;
devuelve la URI normalizada (sin espacios en los extremos, esquema en minúsculas) o `null`.

- Esquemas permitidos, sin distinguir mayúsculas: `https`, `http`, `mailto`, `tel`. Cualquier otro
  (`javascript:`, `intent:`, `file:`, `content:`, `data:`, `market:`, `android-app:`, `sms:`,
  `ftp:`…) se rechaza.
- Sin esquema se rechaza: rutas relativas, `//host`, `#ancla`, `?consulta`, **`www.ejemplo.org`
  (decisión: rechazado; no se adivina `https://`)** y los dos puntos codificados
  (`javascript%3A…`, que no son un esquema).
- Se rechaza cualquier espacio en blanco interior, caracteres de control y caracteres invisibles
  de formato (Unicode `Cf`: cero ancho, anulación bidi), p. ej. `java\nscript:`.
- `http(s)` exige `//host` no vacío y sin credenciales (`usuario@`, que sirve para suplantar
  dominios). `mailto:` y `tel:` exigen algo tras los dos puntos.
- Más de 8192 caracteres se rechaza (límite de tamaño de un Intent).
- Nunca se registra la URL ni el contenido de la nota; `LinkHit` y `SegmentKind.Link` no la
  imprimen en `toString`.

Cobertura: `domain.markdown` está bajo la regla crítica de Kover (100 % de líneas y ramas);
`LinkTargetTest` y `NoteLinksTest` cubren la tabla de esquemas, Unicode, URL muy largas,
paréntesis, trucos de espacios y de `%3A`, CRLF, emoji previo, enlaces adyacentes y énfasis
anidado. `EditorLinksTest` cubre el estado del botón (flujo del cursor) y el toque de solo lectura.

## Pendiente de comprobar en dispositivo

Nunca ejecutado en un dispositivo (sin adb en esta tarea): el botón en la barra con el teclado
abierto, el toque en notas de solo lectura (precisión del toque sobre el último carácter y en
líneas con envoltura), el `Toast` cuando no hay aplicación, y el comportamiento con TalkBack.
