<!--
SPDX-FileCopyrightText: 2026 UltimateNotes contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0012 — Icono de la app

**Decisión (usuario):** opción 4, "hoja y lápiz", sobre coral `#D9483B`.

**Por qué cambiar el anterior:** el icono inicial usaba el mismo azul que UltimateTasks
(`#0B63CE`) y el de UltimateDeck es otro azul (`#2F5DA8`): en el lanzador eran difíciles de
distinguir. Además la capa monocroma reutilizaba el primer plano, que tiene las líneas opacas,
y los iconos temáticos de Android 13+ habrían mostrado una hoja sólida sin texto.

**Cómo está hecho:**
- `drawable/ic_launcher_foreground.xml`: hoja blanca, tres líneas coral, lápiz blanco con
  contorno coral y punta oscurecida.
- `drawable/ic_launcher_monochrome.xml`: capa propia. Las líneas y un hueco de 2 unidades
  alrededor del lápiz son agujeros (devanado opuesto + `clip-path`), de modo que solo cuenta el
  alfa y la silueta sigue siendo legible.
- `values/ic_launcher_background.xml`: color de fondo.
- `fastlane/metadata/android/en-US/images/icon.png`: 512×512 con el mismo dibujo recortado al
  área visible (viewport 18..90).
- Todo está en vectores, sin imágenes de mapa de bits ni fuentes.

**Comprobado:** el dibujo se renderizó a PNG a partir de las mismas geometrías (color y
monocromo) y se revisó a la vista. **No se ha visto en un lanzador real**: máscaras de distintos
fabricantes, tamaño pequeño y icono temático quedan por comprobar en dispositivo.

**Pendiente de decidir:** el color primario de la interfaz sigue siendo el azul `#0B63CE`
(`ui/theme/Color.kt`); con colores dinámicos activados toma el del fondo de pantalla.
