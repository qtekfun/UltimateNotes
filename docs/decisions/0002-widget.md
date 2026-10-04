# ADR 0002 — Widget de inicio con Jetpack Glance (T15)

- Estado: aceptada · Fecha: 2026-10-04 · Tarea: T15
- Hecha sin confirmación previa (el usuario aprobó el plan y no estaba disponible); estas son las decisiones no obvias.

## Dependencias
- `androidx.glance:glance-appwidget` y `glance-material3` 1.2.0 (Apache-2.0). Verificado con `dependencies` que el árbol de runtime no contiene Google Play Services ni Firebase (además lo vigila `checkForbiddenDependencies`). Dependen de WorkManager/Compose, que ya usamos.

## Bloqueo
- **Con el bloqueo activado el widget nunca muestra contenido**, aunque la app esté desbloqueada en ese momento. Motivo: la sesión desbloqueada sobrevive al tiempo en pantalla (el re-bloqueo solo ocurre al volver a la app tras el timeout), y el widget vive en la pantalla de inicio, donde sí se vería con la app en segundo plano. Regla: oculto = `ajuste de bloqueo activado || AppLockState.locked`. La lee `LockConfigSource.current()` y `AppLockState.locked` (T13); no se toca el controlador.
- Estado bloqueado y sin sesión **no leen la base de datos**: `LoadWidgetContent` solo consulta notas cuando va a mostrarlas. Sin sesión gana "iniciar sesión" sobre "bloqueado" (no hay nada que proteger). Nada del contenido se registra en logs.

## Datos y orden
- Selección en `domain/widget` (puro, con tests): favoritas primero, luego `modified` descendente y `localId` descendente como desempate estable; máximo 10 notas; título guardado (`title`, sin recalcular), vista previa de una línea (espacios colapsados, 80 caracteres, sin partir pares sustitutos). Un título vacío se localiza en la UI («Sin título»).
- Consulta propia `NoteDao.recent/observeRecent(limit)` con `LIMIT`: el widget no carga las 5 000 notas.

## Actualización (sin sondeo)
- `updatePeriodMillis=0`. `WidgetChanges` observa: las 10 notas visibles (Room: cubre ediciones locales y cualquier sync, porque el worker escribe en Room), `AppLockState.locked`, el ajuste de bloqueo y la cuenta. Solo emite si cambia lo que el widget dibuja (`distinctUntilChanged` sobre las filas ya reducidas) y agrupa ráfagas con `debounce` de 2 s. `WidgetUpdater` llama a `updateAll` y arranca en `Application.onCreate` (el proceso existe siempre que corre el worker de sync). No se tocó `SyncRunner`: engancharse a la BD evita acoplar sync y widget.

## Navegación
- La app no tenía rutas ni deep links (navegación por estado en `AppRoot`). Se añade una petición mínima: `LaunchRequest` (`OpenNote(id)` / `NewNote`) por extras de un intent explícito a `MainActivity` (no se exporta ningún deep link nuevo). `MainActivity` la lee en un arranque nuevo y en `onNewIntent` (es `singleTop`); no en recreaciones, para no reabrir la nota al girar. `AppRoot` la ejecuta cuando hay sesión y la descarta; si el bloqueo está activo, `LockGate` sigue tapando la app, así que se abre tras desbloquear. Los intents llevan una `data` (`ultimatenotes://note/<id>`) solo para que cada `PendingIntent` sea distinto; no hay filtro de intents para ella.

## Tamaños y aspecto
- `SizeMode.Responsive` con dos tamaños: 110x110 dp (2x2: última nota + botón nueva nota) y 220x180 dp (lista con cabecera y botón). Estados sin notas (vacío, sin sesión, bloqueado) se dibujan igual en ambos y al tocar abren la app; el vacío añade el botón de nota nueva.
- Colores: `GlanceTheme` (colores dinámicos en Android 12+, esquema Material por defecto antes). El widget sigue al sistema (claro/oscuro), no al selector de tema de la app ni a AMOLED: Glance no puede leer esos ajustes de forma fiable al dibujar.
- Botones de 48 dp, `contentDescription` en el botón, la estrella y cada fila.
- Manifiesto: `xml/notes_widget_info.xml` (compatible con API 26) y `xml-v31/` con `description`, `previewLayout` y tamaño objetivo 2x2, para que Lint no marque atributos nuevos como `UnusedAttribute`. `previewImage` (vector) para versiones anteriores a 12.
- Cadenas del widget en `values*/widget_strings.xml` (archivo aparte, para no chocar con otras tareas que tocan `strings.xml`).

## Pendiente (solo verificable en un dispositivo)
- Aspecto real, redimensionado, selector de widgets y la vista previa; el toque en cada fila y en «nota nueva» con la app en frío y en caliente; el estado bloqueado tras activar/desactivar el bloqueo; refresco tras un sync.
- Licencia: `glance-appwidget-external-protobuf` redistribuye protobuf bajo BSD-3-Clause (libre, compatible con GPLv3); se añade a la lista de `licensee`.
