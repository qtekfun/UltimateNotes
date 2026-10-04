<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0003 — Registro de decisiones tomadas sin supervisión

El usuario autorizó fusionar PRs si la CI pasa y seguir el plan sin él presente. Este registro
lista lo decidido en ese tiempo para poder revertirlo o mejorarlo. Cada punto se puede deshacer
con `git revert` del PR indicado.

## Política aplicada
- Se fusiona un PR solo con `check` en verde (y sin conflictos). Los conflictos se resuelven
  conservando ambos lados y volviendo a pasar la CI.
- "Cumple rendimiento": no hay benchmarks. Solo existen las mediciones del spike del editor
  (ADR 0001: ~11 ms para 50 KB en JVM) y los tests. **No se ha medido rendimiento real en
  dispositivo**; queda para T17.
- Sin tablet: el usuario pidió no seguir probando en ella. Ningún agente usa adb.
- No se crean tags ni releases: requieren la clave de firma (`UN_*`) que aún no existe.

## Decisiones
| # | Decisión | Motivo | PR |
|---|---|---|---|
| 1 | El título de la nota es un campo propio (no se deriva del cuerpo). | La API de Notes ≥1.0 lo trata como campo separado y nombre de fichero; `SPEC.md` asumía lo contrario. | #16 |
| 2 | Notas nuevas sin título: se deriva una vez de la primera línea o "Nueva nota". | Igual que la web. | #16 |
| 3 | Un título distinto es un cambio real en conflictos: la copia local conserva título y texto. | Nunca perder datos. | #16 |
| 4 | Un título en blanco en una nota existente se ignora. | Evitar borrar el nombre del fichero por accidente. | #16 |
| 5 | Botón "Sincronizar ahora" en la barra superior; el spinner de pull-to-refresh sigue el estado real. | Petición del usuario. | #17 |
| 6 | Backup: formato propio `UNBK` (PBKDF2 600k + AES-256-GCM); el bloqueo de la app no se restaura. | Evitar bloquear un móvil sin pantalla de bloqueo. | #15 |
| 7 | Ediciones ganan a borrados en ambos sentidos; 404 en PUT recrea la nota. | DELETE no puede ser condicional. | #9 |
| 8 | Exportar solo desde el menú del editor; el `.md` lleva solo el cuerpo (el título va en el nombre del fichero). | Mantener el Markdown fuente intacto. | #18 |
| 9 | El widget queda bloqueado mientras el ajuste de bloqueo esté activo, aunque la app esté desbloqueada; sigue el modo claro/oscuro del sistema, no el tema de la app. | La sesión de desbloqueo sobrevive a la app en pantalla. Ver ADR 0002. | #20 |
| 10 | E2E contra Nextcloud real en Docker (`e2e.yml`), no contra el servidor del usuario. | Validar sin dispositivo ni cuenta. Ver ADR 0004. | #21 |
| 11 | Contraseña solo al host de la cuenta; se rechazan redirecciones https→http y URLs con credenciales. Guarda en `check` contra dependencias prohibidas. | Revisión de seguridad. Ver ADR 0005. | #22 |
| 12 | Receta F-Droid con `commit` y `AllowedAPKSigningKeys` como `TODO`; sin capturas fabricadas. Ver ADR 0006. | Solo el mantenedor puede rellenarlos. | #23 |
| 13 | Bug real corregido: `close()` cancelaba un autoguardado en curso y no se pedía el sync. Test intermitente estabilizado (30/30). Ver ADR 0007. | Hallado al estabilizar el test. | #25 |
| 14 | Capturas de F-Droid con un test instrumentado y datos de demostración; script que exige indicar el dispositivo. Ver ADR 0008. | No requiere cuenta ni servidor. | #24 |
| 15 | Previsualizaciones y fragmentos de búsqueda en texto plano (`PlainText`). | El Markdown crudo se veía en la lista. | feat/clean-markdown-previews |
| 16 | El editor mantiene visibles los símbolos de Markdown en el 1.0. | Decisión del usuario (2A); revisar para 1.1. | — |
| 17 | `minSdk` sube de 26 a 31. | Decisión del usuario: no se instalan emuladores y T17b no puede cubrir API 26/30. Se elimina el flujo de bloqueo para Android 8–10, las carpetas `-v31` y las comprobaciones de versión. | feat/min-sdk-31 |
| 18 | Icono: opción 4 (hoja y lápiz), primero en coral y luego en el azul de la app `#0B63CE` a petición del usuario. | Coincide con el fondo del icono de Tasks; ver 0012. | feat/icon-pencil, feat/icon-app-blue |
| 19 | `v0.1.0-rc.1` publicada como pre-release; la firma coincide con la huella indicada por el mantenedor. | Primera ejecución real del workflow de release. | #37 |
| 20 | T17b se da por hecho con excepciones aceptadas: voz real de TalkBack, desplazamiento con gesto real y arranque en release a lista. | Decisión del mantenedor; la biometría la probó él. | #34, #35 |

## Deuda conocida
- (Resuelta en #25) Test intermitente `EditorViewModelTest ... after the debounce`.
- `ObserveFoldersTest` falló dos veces en una máquina muy cargada y pasa solo; vigilar.
- El pull inicial de 5 000 notas tarda ~8 s en JVM (una transacción por nota); puede ser peor en
  un teléfono. Sin tocar: el motor de sync exige 100 % de cobertura y no hay medición en dispositivo.
- (Resuelta, ver ADR 0011) Limitación de sync: favorito/carpeta de un cliente contra edición de texto
  de otro dejaba una copia "(conflicto …)" extra sin pérdida de texto (ADR 0004). Ahora se fusiona
  campo a campo contra una base por nota.
- Las instalaciones en el OPPO PGEM10 fallaron con `Failure [-99]` (confirmación de OPPO sin
  aceptar); nada se ha ejecutado en él.
- La tablet Huawei (MRO-W09) conserva la app de depuración y el APK de tests instrumentados
  (`com.qtekfun.ultimatenotes.test`) instalados por un agente. El test
  `tappingACheckboxTogglesExactlyOneCharacterAndUndoRestoresIt` falla ahí también en `master`
  (coordenadas ajustadas a un móvil).
- Sin validar en dispositivo: IME real, casillas, TalkBack, biometría, SAF, sync contra un
  servidor real (solo `FakeNotesServer` en JVM).
- Los worktrees de agentes viven en `.claude/` (no ignorado en git).
