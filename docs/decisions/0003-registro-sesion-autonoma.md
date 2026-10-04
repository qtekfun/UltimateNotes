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

## Deuda conocida
- Test intermitente en CI: `EditorViewModelTest > renaming an existing note saves the title only,
  after the debounce` falló una vez y pasó al relanzar. Mezcla tiempo virtual y espera real sobre
  hilos de Room. Pendiente de estabilizar.
- La tablet Huawei (MRO-W09) conserva la app de depuración y el APK de tests instrumentados
  (`com.qtekfun.ultimatenotes.test`) instalados por un agente. El test
  `tappingACheckboxTogglesExactlyOneCharacterAndUndoRestoresIt` falla ahí también en `master`
  (coordenadas ajustadas a un móvil).
- Sin validar en dispositivo: IME real, casillas, TalkBack, biometría, SAF, sync contra un
  servidor real (solo `FakeNotesServer` en JVM).
- Los worktrees de agentes viven en `.claude/` (no ignorado en git).
