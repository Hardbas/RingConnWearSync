# Tile Design System — One UI Watch 8 parity for the 5 RingConn tiles

Feature branch: `feat/tile-design-system`. Scope: `wear/src/main/java/io/github/hardbas/wearsync/app/tile/**`, `wear/src/main/res/drawable/tile_bg_*.xml`, `wear/src/test/java/io/github/hardbas/wearsync/app/tile/**`. Out of scope: complications, detail activities, phone module, data model, strings content (only keys already present; if a string is unavoidable it is added in T1 and workers do not touch `strings.xml`).

## Specs

S1 — **Paleta única.** Los cinco `*TileService.kt` no contienen literales `0x……` de color de UI: todos los colores salen de `TileDesign` (neutros + rampas de acento). Los únicos hex permitidos en un tile son los de su rampa de estado, y aun así se declaran en `TileDesign`.

S2 — **Jerarquía tipográfica.** El número héroe de cada tile usa ≥38sp, peso bold, `FontSetting.tabularNum()` y tracking negativo. Las etiquetas usan 11sp bold con `letterSpacing` positivo. La relación héroe/etiqueta es ≥3.0.

S3 — **Profundidad de dos capas.** Cada tile pinta un fondo full-bleed con gradiente diagonal como primer hijo del `rootBox`, mediante `Image` + VectorDrawable `tile_bg_<metric>.xml`. No se usa `Background.setBrush` (no existe en protolayout 1.2.1: `Background.Builder` sólo acepta `ColorProp`).

S4 — **Cromo acotado.** Cada tile apila como máximo 4 bloques verticales y muestra como máximo 1 badge y 1 píldora de acción. Ningún hex de píldora se repite por tile: alto/radio salen de `TileDesign.Chrome`.

S5 — **Entrada animada en los cinco.** Todos los tiles animan el arco con `DynamicFloat.animate(from, to, spec)` (no keyed por `AppDataKey`), para que el gesto se vea al entrar al tile aunque el dato no haya cambiado.

S6 — **Presupuesto de animadores ≤2 por tile.** Contrato verificable: como máximo dos `AnimationSpec` por tile en el árbol de layout, y ninguno infinito sobre el héroe (el bloque del número).

S7 — **Segundo gráfico por métrica.** Además del arco de bisel, cada tile tiene un gráfico propio dentro de la misma familia visual: HR escalera de 5 zonas, Vitals barra SpO2, Stress escala de 4 niveles, Steps barra de meta, Sleep conserva el cielo con luna y horizonte.

S8 — **Arco One UI.** El arco activo usa puntas redondeadas (`STROKE_CAP_ROUND`) y `SweepGradient` de acento→acento transparente, manteniendo `setColor` como fallback explícito (campos separados en el proto: color y brush coexisten).

S9 — **Sleep no gana animadores.** `SleepTileService` conserva como máximo 2 animadores (atardecer + barrido) y no incorpora respiración infinita ni halo.

## Tasks

- T1 — Fundación: `TileDesign` (paleta, tipografía, chrome, motion, arco) + 5 `tile_bg_*.xml` + contrato de test compartido. Specs: S1-S3, S6, S8. Commit: `feat(tiles): add One UI design system foundation`
- T2 — HeartRateTileService + su contract test. Specs: S1-S8 (S7 = escalera de zonas). Commit: `feat(tile-hr): apply design system, hero scale and zone ladder`
- T3 — VitalsTileService + su contract test. Specs: S1-S8 (S7 = barra SpO2). Commit: `feat(tile-vitals): apply design system and SpO2 bar`
- T4 — StressTileService + su contract test. Specs: S1-S8 (S7 = escala de 4 niveles). Commit: `feat(tile-stress): apply design system and stress scale`
- T5 — DailySummaryTileService + su contract test. Specs: S1-S8 (S7 = barra de meta). Commit: `feat(tile-steps): apply design system and goal bar`
- T6 — SleepTileService + su contract test. Specs: S1-S5, S7-S9. Commit: `feat(tile-sleep): align celestial tile with the design system`

## Log

L1 — «como ves el diseno de todos los tiles de mi watch de ringconapp? yo siento que les falta algo... no se los veo muy sosos que tipo de animacion y diseno es compatible con los tiles de samsung?»

L2 — «bueno arregla todo lo que enconraste pues»

L3 — Hallazgos aceptados por L2 (inventario que define el alcance): (1) jerarquía plana, héroe 30sp; (2) sin letterSpacing ni `tnum`; (3) profundidad One UI perdida, `oneui_bg_*_capsule.xml` huérfanos y `rootBox` sin fondo; (4) cromo excesivo, hasta 4 chips por tile; (5) movimiento mal ubicado, 4 de 5 tiles sólo animan keyed por estado; (6) riesgo de repetición del atardecer de Sleep cada 60s; (7) 57 ARGB sueltos entre 5 archivos; (8) puntas de arco sin redondear; (9) arco plano sin gradiente; (10) una sola plantilla para 5 tiles.

L4 — Evidencia de entorno: `./gradlew :wear:compileDebugKotlin` verde en 4s. `adb devices` vacío → no hay verificación en el `SM-L500` disponible en esta sesión; el cierre lo reporta como pendiente y no se inventa.

L5 — Decisión de implementación: el fondo con gradiente NO se hizo con `Image` + VectorDrawable. Se verificó contra `protolayout-1.3.0` (la versión que resuelve el proyecto, no 1.2.1) que `ModifiersBuilders.Background.Builder` acepta `setBrush`, y que `LinearGradient` existe. El fondo sale de `TileBackdrop` (tokens), sin XML duplicando la paleta. Los 5 `tile_bg_*.xml` se crearon y se borraron en el mismo turno.

L6 — Trampa de API encontrada durante la calibración: `Repeatable` sin `iterations` es infinito por protocolo y serializa bien, pero el getter de Kotlin `getIterations()` lanza `IllegalStateException: Repeatable has infinite iteration.`. El `toString()` del `Repeatable` también lanza. Consecuencia: los tests no pueden inspeccionar `iterations` ni imprimir el repeatable; `hasInfiniteIteration()` sí es seguro. El pulso que ya estaba en producción no era un bug.

L7 — Costura de testabilidad: los tiles no eran testeables en la JVM porque `buildTile` recibía `Context` sólo para `getString`. Se introdujo `TileStrings` + `AndroidTileStrings` y un parámetro `packageName` explícito. Eso hizo posible el contrato por tile y eliminó el `PACKAGE_NAME` hardcodeado.

L8 — Falso verde corregido: el contador de animadores de los tests de HR y Vitals recorría el árbol de elementos con una rama `is ArcLine` siempre falsa, así que no contaba el arco. Se reemplazó por dos recorridos (árbol + `Arc.contents`, incluido `ArcModifiers.opacity`) y la aserción pasó a `assertEquals(2, …)`. El worker probó que la aserción puede fallar mutando la expectativa a 3.

L9 — Entorno: `adb devices` vacío en toda la sesión. Ninguna verificación visual en el `SM-L500`. Todo lo verificado es estructural (74→75 tests verdes, contrato de diseño, auditoría de fuente) más el build release.
