<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0010 — T17b en dispositivo (OPPO CPH2841, Android 16)

Todo se ejecutó en un único móvil (OPPO CPH2841, Android 16, 1440x3168, 90 Hz, español), con la build **debug**. Los tests usan solo notas inventadas en bases de datos propias (en memoria o ficheros `t17b-*.db` que se borran); nunca la base ni la cuenta del usuario. La matriz de API 26 / 30 queda descartada (minSdk pasa a 31).

Ajustes del sistema tocados: solo `font_scale` (1.0 -> 1.3 / 2.0 -> 1.0). Originales y finales verificados: `font_scale` 1.0, `accelerometer_rotation` 1, `user_rotation` 0. La rotación se hace con `Activity.requestedOrientation` (misma recreación que una rotación real) y no toca ajustes del sistema.

## 1. Fuente al 130 % y 200 %
`FontScaleTest` (6 pruebas por escala, `scripts/t17b-font-scale.sh -t 130`, que fija la escala, comprueba que es la esperada, y restaura el valor). Pantalla principal con la barra flotante, cajón, editor (con teclado, también en horizontal), búsqueda y ajustes: controles dentro de la ventana, sin solaparse, accesibles con scroll, etiquetas sin recortar, texto del editor con al menos 48 dp con el teclado abierto. **Resultado: 12/12 correctas.** Capturas revisadas a mano (`font200_*`): sin controles recortados.
Observaciones sin cambiar: al 200 % la fecha se pega al texto de la vista previa; en horizontal con el teclado abierto al 200 % el cuerpo del editor muestra solo ~1,5 líneas (el título no se desplaza con el texto): usable, pero justo.

## 2. Rotación
`RotationTest` (6 pruebas): editor con texto/título/cursor, teclado abierto, 6 rotaciones seguidas escribiendo, antes del autosave, cajón abierto, carpeta elegida, búsqueda con consulta, ajustes y recreación de la actividad. **6/6 correctas, sin pérdida de datos** (la nota se guarda con el texto exacto; no se duplica). Comportamiento a conocer: tras rotar se conserva el cursor pero la selección resaltada se colapsa (el campo reinicia su sesión de entrada).

## 3. Accesibilidad (sin TalkBack)
Dependencia nueva de test: `androidx.compose.ui:ui-test-junit4-accessibility` (Apache-2.0), que trae el Accessibility Test Framework de Google (Apache-2.0). `AccessibilityAuditTest` (framework sobre la ventana + árbol de semántica) y `ComposeAccessibilityChecksTest` (`enableAccessibilityChecks()`, que audita cada pantalla al pulsar/escribir en los flujos principales). **Resultado: 0 errores.** Hallazgos arreglados en producto:
- Los títulos de sección de Ajustes no eran encabezados (`heading()`): añadido en `SettingsScreen`.
- Contraste de los marcadores Markdown del editor y de las tareas hechas (1,5 a 1,95 según el framework): `EditorScreen` sube `MARKER_ALPHA` 0,6 -> 0,75 y `DONE_ALPHA` 0,55 -> 0,7. Los avisos que quedan son de botones deshabilitados (deshacer/rehacer), exentos.
Verificado por semántica: todos los controles con clic tienen etiqueta, la carpeta actual se anuncia como seleccionada, los botones tienen rol, el botón deshabilitado se anuncia como tal, interruptores y filas de opción tienen estado y rol, y el campo de la nota ofrece la acción personalizada de marcar/desmarcar casilla. **NO se probó la voz real de TalkBack** (no se activó ningún servicio de accesibilidad): orden de lectura, frases y gestos siguen sin verificar.

## 4. Biblioteca de 5 000 notas (build debug, medianas; datos sintéticos de 0,2 a 20 KB, 30 carpetas, 2 % favoritas)
| Medida | 500 | 1 000 | 5 000 |
|---|---|---|---|
| Lanzar actividad -> primera fila de la lista | 1,0 s | 1,06 s | **1,6 s** |
| Primera consulta de la lista (todas las filas con texto) | 20 ms | 38 ms | 142 ms |
| `toListItem` + secciones (CPU) | 57 ms | 105 ms | 647 ms |
| Árbol de carpetas | 8 ms | 6 ms | 27 ms |
| Búsqueda FTS, una palabra (mediana) | 13 ms | 17 ms | 55 ms |
| Búsqueda FTS, dos palabras | 6 ms | 9 ms | 25 ms |
| Teclear una palabra -> primer resultado en pantalla | 0,62 s | 0,64 s | 0,92 s (incluye el debounce) |
| Abrir cajón | 154 ms | 125 ms | 156 ms |
| **Primer pull (motor real, Room en fichero, servidor falso en proceso)** | 435 ms | 620 ms | **2,8 s** (1 786 notas/s) |
| Segundo sync sin cambios | 6 ms | 1 ms | 4 ms |

- El pull de 5 000 notas tarda 2,8 s en el móvil (frente a ~8 s en el PC compartido): **no hay motivo para agrupar transacciones por rendimiento**. No incluye red ni JSON (el servidor falso va en proceso). No se tocó el motor.
- El arranque a lista con 5 000 notas (1,6 s en debug, sin contar el arranque del proceso) **no cumple todavía el objetivo de SPEC §10 (< 1 s)**, aunque debug es más lento que release. La lista lee todas las filas con su contenido completo y las mapea (150 ms + 650 ms con 5 000), y se repite en cada cambio de la base. Es el punto a optimizar (columna de vista previa o paginación) si se confirma en release.
- **Jank al desplazar: NO medido de forma fiable.** El inyector de gestos de Compose avanza a su propio ritmo (46 fotogramas con 65 % lentos, igual con 500 que con 5 000 notas: es del arnés). La inyección de eventos por `UiAutomation`, `dispatchTouchEvent` y `input swipe` no desplazó la lista en este móvil. Queda pendiente medir con un gesto real o con una build release.

## 5. Ciclo de vida (app real instalada, con la cuenta del usuario)
- **Actualización en sitio**: `adb install -r` del mismo APK debug sobre la app instalada: `firstInstallTime` intacto y SHA-256 de `ultimatenotes.db` y de `account.xml` idénticos antes y después; la app abre en la lista, no en el login. La ruta de migración Room 1->2 la cubre `MigrationTest` (en dispositivo). No se probó desde una versión antigua real (no hay APK previo instalable sin desinstalar).
- **Muerte del proceso** (`am kill` con la app en segundo plano, solo nuestro paquete): se creó una nota `[test] t17b proceso` desde la UI, se mató el proceso y se relanzó. **Bug encontrado**: la nota sobrevivía (estaba guardada) pero la app volvía a la lista, no al editor, porque `AppRoot` guardaba `openNote`/`inSettings` con `rememberSaveable(destination)` y `destination` empieza en `LOADING` tras la muerte del proceso, lo que descartaba el estado guardado. Arreglo: la clave pasa a ser `destination == LOGIN`. Tras el arreglo, el editor de la nota se restaura tras `am kill`. Limitación: una nota nueva ya creada se restaura como un editor nuevo vacío (se descarta al salir; la nota escrita sigue en la lista). La nota `[test]` se borró después desde la UI y la sync quedó en reposo.
- **Bloqueo biométrico**: no se pudo ejercitar (sin diálogos del sistema).

## 6. Higiene del log
Se revisó `logcat --uid` de la app tras todas las ejecuciones: 0 `FATAL EXCEPTION`, 0 ANR, 0 StrictMode, y ni títulos ni texto de notas ni credenciales (se buscaron títulos/texto de las notas de demostración y de la nota `[test]`, `password`, `Authorization`). Solo ruido del sistema/OEM.

## No se pudo / pendiente
- Voz real de TalkBack, orden de lectura y gestos.
- Fluidez al desplazar con un gesto real, y arranque en build release (ver §4).
- Biometría (requiere diálogos del sistema).
- Matriz de API: descartada (minSdk 31); solo se probó Android 16.
- Pull con red y JSON reales (en dispositivo solo contra el servidor falso en proceso).
- Archivos añadidos: `app/src/androidTest/.../quality/` (arnés `DeviceTestBase`, tests y `SyntheticNotes`), `scripts/t17b-font-scale.sh`, y `FakeNotesServer` pasa a `app/src/sharedTest` para usarlo también desde `androidTest`.
