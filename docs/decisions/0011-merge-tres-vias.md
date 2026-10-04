<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0011 — Fusión de tres vías campo a campo

Resuelve la limitación conocida de los ADR 0003 y 0004: si un cliente solo cambiaba el favorito o la
carpeta y otro editaba el texto de la misma nota, el 412 se trataba como conflicto de texto y
aparecía una copia `(conflicto fecha)` espuria. El usuario usa varios dispositivos a la vez, así que
se corrige. Se deshace con `git revert` del PR (el esquema v3 quedaría en las instalaciones ya
migradas; ver "Migración").

## Diseño
**Base por nota.** Cada fila guarda la última versión del servidor que el cliente conoció, solo de
los campos fusionables: texto, título, carpeta y favorito (`NoteEntity.base: NoteBase?`, cuatro
columnas `baseContentHash`, `baseTitle`, `baseCategory`, `baseFavorite`). **El texto se guarda como
hash SHA-256, no como segunda copia**: la fusión solo necesita saber si cada lado conserva el texto
base, nunca cuál era. Así la fila no crece, y las consultas `SELECT *` de las listas (5 000 notas) no
cargan el doble de texto. (Es el "texto o su hash" del ADR 0004; elegido el hash.)

**Cuándo se actualiza la base.** Siempre con lo que el servidor tiene tras el evento:
- subida aceptada (`completePush`): lo enviado, con el título saneado que devuelve el servidor
  (también si el usuario editó mientras tanto o borró: la base es lo que hay en el servidor);
- adopción de una nota del servidor (`toSyncedEntity`: pull, título saneado, 412 resuelto);
- resolución por mismo texto sin base (`adoptEtag`): base = servidor.
- **Nunca** en una copia de conflicto (`NEW`, sin base) ni al recrear una nota (`asNew`, base nula).

**Fusión** (`ConflictResolver`, ante etag distinto con la nota local `DIRTY`, sea en el 412 o en el
pull), con `L` = local, `S` = servidor, `B` = base:

| Campo | `L == B` | `S == B` | cambiaron ambos |
|---|---|---|---|
| texto, título | se toma `S` | se toma `L` | igual (`L == S`): se toma, en silencio; distinto: **conflicto** |
| carpeta, favorito | se toma `S` | se toma `L` | **gana el local** (última intención del usuario), documentado, sin conflicto |

- Un título local en blanco cuenta como "no editado" (la app nunca guarda uno vacío en una nota
  existente), como hasta ahora.
- **Conflicto** (texto o título distintos en ambos lados): política de siempre. El servidor se queda
  en la nota original (texto y título) y el texto y título locales pasan a una nota NUEVA
  `"<título> (conflicto <fecha>)"` en la carpeta local. Se tratan juntos texto y título: si cualquiera
  choca, ambos del lado local van a la copia. Novedad: la carpeta/favorito que el usuario cambió
  localmente sí se conservan en la nota original (queda `DIRTY` y se sube después).
- Un texto local en blanco con el texto del servidor cambiado: gana el servidor (como antes), salvo
  que el título choque.
- Resultado sin conflicto: la fila toma el etag y la base del servidor; queda `SYNCED` si el resultado
  coincide con el servidor y `DIRTY` si hay algo local que subir (se sube con `If-Match` del etag
  nuevo; si el servidor vuelve a cambiar, se fusiona de nuevo contra la base nueva).
- Sin línea a línea: el texto es atómico (cambió o no). Fusionar por líneas es demasiado arriesgado.
- Sin base (filas `DIRTY`/`NEW`/`DELETED` al migrar, o nunca sincronizadas): comportamiento
  conservador anterior. Mismo texto y título adopta el etag (carpeta/favorito locales distintos
  quedan `DIRTY`); cualquier otra cosa es conflicto.

Sin pérdida de datos: cada valor de texto o título escrito por un usuario sobrevive en el servidor
salvo que ese mismo cliente lo sobrescribiera después. Un favorito o carpeta pisado por el cambio
local de otro cliente no es pérdida de texto: es el valor elegido por la regla anterior.

## Migración (Room v2 → v3)
`Migration2To3`: cuatro `ALTER TABLE ADD COLUMN` anulables y, para las filas `SYNCED` (iguales al
servidor por definición), base = valores actuales (el hash se calcula en Kotlin: SQLite no trae
SHA-256). Las filas `DIRTY`/`NEW`/`DELETED` quedan sin base. Esquema exportado en
`app/schemas/.../3.json`. Tests: `Migration2To3Test` (JVM, SQLite real) y un caso en el
`MigrationTest` instrumentado (compilado en `check`, no ejecutado: sin dispositivo).

## Pruebas
- `ConflictResolverTest`: toda la tabla anterior, con y sin base.
- `SyncEnginePullTest` / `SyncEnginePushTest`: favorito/carpeta/título contra edición remota por el
  camino del pull y por el del 412, sin copia.
- `SyncMergeTest` (propiedad, 200 semillas): dos clientes cambian un subconjunto aleatorio de
  {texto, título, carpeta, favorito}; si texto y título no chocan, ni copia ni pérdida y el valor de
  cada campo es el esperado; si chocan, convergen y no se pierde ninguno.
- `SyncConvergenceTest` (propiedad previa, con fallos y ediciones durante el sync): sigue verde.
- E2E contra Nextcloud 33.0.9 / Notes 6.1.0: los tests que fijaban la copia extra ahora exigen una
  sola nota fusionada (favorito, carpeta, renombrado y combinados); el choque de texto sigue dando
  una copia.
- Cobertura crítica (sync/queue, sync/conflict): 100 % de líneas y ramas.

## Decisiones
| # | Decisión | Motivo |
|---|---|---|
| 1 | Hash SHA-256 del texto base en lugar de copia del texto. | No duplicar el almacenamiento ni cargarlo en cada lista. |
| 2 | Carpeta/favorito en conflicto: gana el local. | Es la última intención del usuario; no hay texto en juego, y se converge igualmente. |
| 3 | Texto y título chocan "juntos". | Mantener la política de copia sin variantes: servidor en la original, local entero en la copia. |
| 4 | Base nula = comportamiento anterior. | Conservador y sin riesgo para datos previos a la migración. |
| 5 | La base de una subida es lo enviado, aunque se editara durante el envío. | Es lo que el servidor tiene; la edición posterior sigue `DIRTY` y se fusiona bien. |
