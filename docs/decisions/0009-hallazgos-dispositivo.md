<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0009 — Hallazgos de la primera ejecución en dispositivo

Primera ejecución instrumentada real: OPPO CPH2841, Android 16, 1440x3168, idioma español. De 36 tests fallaban 6. Resultado final: **36/36** (`am instrument`, sin `ScreenshotTest`) más las capturas.

| # | Hallazgo | Veredicto y arreglo |
|---|---|---|
| 1 | Casilla del editor no cambiaba | **Fallo del test, no del producto.** El nodo de semántica del campo y el `pointerInput` miden lo mismo (1328 px en una pantalla de 1440) y ya están dentro del padding: el layout de texto usa las coordenadas del nodo. El test sumaba otra vez `TEXT_PADDING` y pulsaba 16 dp abajo y a la derecha, fuera de la casilla. Ahora usa el centro del glifo tal cual; pasa y deshace. |
| 2 | Botones de 40 dp | **El área táctil real es 48 dp.** `IconButton` dibuja 40 dp y Material la amplía con `minimumInteractiveComponentSize`; solo `touchBoundsInRoot` lo refleja (el tamaño de layout del nodo sigue siendo 40). El test mide ahora `touchBoundsInRoot`. Producto sin cambios. |
| 3 | `SearchScreenTest` | Dos causas, ambas del test: (a) la `ComponentActivity` de prueba no es de borde a borde y hace *pan* al abrirse el teclado (el campo se enfoca solo), por lo que el contenido quedaba fuera de pantalla; ahora el test usa `enableEdgeToEdge` y `adjustNothing` como la app, que se encarga de los insets; (b) literal inglés `"In Work"` en un dispositivo en español: ahora sale de `R.string.search_scope_folder`. Además, el campo recibe un estado fijo y notifica `""` al perder el foco: se comprueba el texto antes de pulsar el resultado. |
| 4 | `MigrationTest` | El fichero `migration-test.db` sobrevivía entre ejecuciones. Se borra (y sus `-wal/-shm/-journal`) antes y después de cada test. |
| 5 | `ScreenshotTest` | Fallaba al cerrar el cajón con Back programático (la actividad se cerraba, sin jerarquía Compose). Ahora se pulsa "Todas las notas" (cierra el cajón como cualquier carpeta) y las esperas toleran la ventana en la que la jerarquía aún no está registrada. El cajón sigue compuesto al cerrarse, por lo que no se espera a que desaparezca. El último error de sync (preferencias propias de la app) se limpia con `markSynced` para que el botón no salga en rojo. |
| 6 | **Bug de producto hallado en las capturas** | Con el sistema en oscuro y la app en claro (o al revés), la hora y la batería de la barra de estado salían blancas sobre fondo claro: `enableEdgeToEdge` sigue al tema del sistema, no al de la app. `UltimateNotesTheme` ahora fija el color de los iconos de las barras según la luminosidad del fondo del tema real. |

## Capturas
`scripts/capture-screenshots.sh -t 130`: 5 por idioma (en-US, es-ES) en `fastlane/metadata/android/*/images/phoneScreenshots/`, con modo demo (12:00, batería 100, sin notificaciones) y solo notas de demostración. Revisadas una a una. Modo demo apagado al final (`sysui_demo_allowed` = 0).

Observaciones sin cambiar: las vistas previas de la lista y los fragmentos de búsqueda muestran el Markdown crudo (`**Día 1**`, `- [x]`); la barra flotante tapa parcialmente la última fila (es el diseño: la lista se desplaza bajo ella).

## Pendiente de T17b
Verificado en este dispositivo (Android 16): `connectedDebugAndroidTest` equivalente, objetivos táctiles de 48 dp, casilla del editor, capturas. Sigue pendiente: matriz API 26 / 30, TalkBack, fuente al 200 %, rotación, fluidez con 5 000 notas.
