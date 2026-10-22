# Editorial Cálida — the five watch tiles, second redesign

Feature branch: `feat/tile-design-system`. Source of truth: `design/_render/RingConnWearSync · Edición Claude Design (1).html` (five 192dp tiles, palette `warm`, motion `Esencial`). Scope: `wear/src/main/java/io/github/hardbas/wearsync/app/tile/**`, `wear/src/main/res/drawable/tile_*.xml`, `wear/src/test/java/io/github/hardbas/wearsync/app/tile/**`. Out of scope: complications, detail activities, phone module, data model, `strings.xml` content (existing keys only; Spanish comes from `values-es`).

## Specs

S1 — **Paleta única y cálida.** Ningún `*TileService.kt` contiene literales `0x……` de color: todo sale de `TileDesign`. Los únicos valores son la rampa cálida del diseño (`pulse #F2A68D→#D96B4E`, `spo2 #93BEDB→#5E97C4`, `batt #AED09E→#75A465`, `spec #75A478/#A8BE72/#E4B860/#DF8556/#C95252`, `steps #A5C896→#73A559`, `kcal #F0C26B→#D97757`, `teal #86C9BC`, `sleep #7B73B8/#B486C9/#82ADC9/#E3B86B`), el fondo `#080807`, el texto `#F5F2EB` / `#B8B4AA` y la píldora `#262522` con borde `#4A4742`. Los degradados de fondo por métrica desaparecen: el fondo es negro OLED con un resplandor radial del acento al 22%.

S2 — **Jerarquía tipográfica del diseño.** Héroe: 50sp (44sp en Vitales, 38sp en Pasos), peso 500, `tabularNum()`, tracking −0.03em, color `#F5F2EB`. Etiqueta: 10.5sp peso 600, tracking +0.09em, mayúsculas (Sueño la mantiene en caja alta/baja a 12sp, tracking +0.02em, como el diseño). Subtítulo: 12sp peso 500, `#B8B4AA`. Píldora: 10sp tabular. Héroe/etiqueta ≥ 3.0.

S3 — **Geometría del bisel.** Un solo arco de 260° que arranca en 140°, radio 84dp (contenedor de 180dp), trazo 12dp, puntas redondeadas. Anillos concéntricos cuando el tile lleva dos lecturas: exterior radio 85 (trazo 10.5 Vitales / 11.5 Pasos) e interior radio 71 (contenedor 152dp). La píldora mide 28dp de alto, radio 14, centro 63dp debajo del centro del tile. El resplandor radial ocupa 156dp (inset 18dp).

S4 — **Presupuesto de dos animadores.** Cada tile usa exactamente dos: (1) entrada del arco de valor por rotación −25°→0° en 900ms `cubic-bezier(.05,.7,.1,1)`, una vez; (2) respiración infinita con reversa del halo del arco (opacidad 0.10→0.32 en 2600ms) o, en Sueño, del resplandor radial (0.35→0.60 en 5500ms). Ningún animador toca el héroe. Sin datos: se apagan (1) y (2), como el diseño (`display:none`).

S5 — **Lecturas conservadas.** HR: bpm, zona Z1–Z5 (`<62`, ≤96, ≤128, ≤154, resto) y HRV. Vitales: bpm, SpO2 y batería del anillo. Estrés: índice, nivel (Calma <30, Normal <60, Medio <80, Alto) y HRV. Pasos: pasos (con `12.4k` a partir de 10 000), minutos activos, kcal y km. Sueño: score, calidad, duración y desvío de temperatura cutánea (`+0.3°`).

S6 — **Segundo gráfico por métrica.** HR y Estrés: marcador perlado (doble anillo marfil+acento) sobre el arco. Estrés: el arco entero es el degradado de cinco paradas con muescas en 30/60/80. Vitales y Pasos: dos anillos concéntricos. Sueño: cuatro tramos proporcionales a las fases (profundo, REM, ligero, despierto) con estrellas y luna.

S7 — **Estados degradados.** Vacío: héroe `--`, sin arco de valor, sin halo y sin perla; se conserva el track. Viejo (`RingConnVitals.isStale`, >30 min): acentos en `#6E6A62` y héroe al 55% de opacidad, sin animadores.

S8 — **Cuerda respetada.** Cada línea de texto declara su `y` respecto del centro y su ancho estimado debe quedar por debajo de `TileDesign.Layout.safeWidthDp(y)`. La píldora declara su ancho estimado contra la cuerda en su centro (y = +63dp).

S9 — **Ticks horológicos sin coste de elementos.** Las marcas del dial (40 en HR, 36 en los demás, mayores cada 11) y las muescas de Estrés viven en VectorDrawables estáticos de 192dp; no se emiten como `ArcLine` por marca.

S10 — **Motor real.** Todo se construye con `protolayout 1.3.0`: `ArcLine` + `SweepGradient` (`setColor` como fallback explícito), `Image` (drawables) y `Modifiers` (opacidad, `Transformation.rotation`, `Background`). Sin `AnimatedVectorDrawable`, Lottie, Canvas ni dibujo propio. Anchos siempre estáticos.

## Tasks

- T1 — Fundación: `TileDesign` v2 (paleta, tipo, geometría, motion), `TileGauge` v2 (track, arco con degradado, anillo, perla, halo), `TileChrome`/`TileFrame` (glow, píldora, stack), drawables `tile_warm_glow`/`tile_ticks_*`/`tile_stars`/`tile_moon_badge`, `TileTestKit` y contrato de diseño. Specs: S1-S4, S9. Commit: `feat(tiles): warm editorial token and gauge foundation`
- T2 — `HeartRateTileService` + contrato. Specs: S1-S8 (S6 = perla). Commit: `feat(tile-hr): warm editorial dial with zone and HRV`
- T3 — `VitalsTileService` + contrato. Specs: S1-S8 (S6 = anillos SpO2/batería). Commit: `feat(tile-vitals): concentric SpO2 and ring battery rings`
- T4 — `StressTileService` + contrato. Specs: S1-S8 (S6 = espectro y muescas). Commit: `feat(tile-stress): five-stop stress spectrum with notches`
- T5 — `DailySummaryTileService` + contrato. Specs: S1-S8 (S6 = anillos pasos/kcal). Commit: `feat(tile-steps): warm rings and colour-coded activity pill`
- T6 — `SleepTileService` + contrato. Specs: S1-S8 (S6 = fases, estrellas). Commit: `feat(tile-sleep): phase ring, stars and warm moon`
- T7 — Verificación: `:wear:testDebugUnitTest`, `:wear:assembleDebug`, auditoría de literales y reporte de hardware. Commit: `chore(tiles): verify warm editorial redesign`

## Log

L1 — «en la carpeta de desing tienes el nuevo diseno D:\Users\jeiss\ringconn_re\RingConnWearSync\design\_render RingConnWearSync · Edición Claude Design (1) que quiero implemetnar»

L2 — Lectura del HTML de diseño (60 514 bytes). Aporta: paletas `warm`/`contrast`, `STATE` con `live`/`stale`/`empty`, movimiento `Esencial`/`Expresivo`, y por tile el árbol exacto (anillos, radios, trazos, perla, muescas, ticks, héroe por tamaño). Se implementa `warm` + `Esencial`; `contrast` y `Expresivo` no tienen interruptor en el reloj y `Expresivo` no es expresable en ProtoLayout (cifras que cuentan, sheen de texto, estrellas fugaces).

L3 — Decisiones de traducción a ProtoLayout: (a) el resplandor radial no existe como brush (1.3.0 sólo tiene `LinearGradient` y `SweepGradient`), va como `Image` de un VectorDrawable con gradiente radial, tintado con el acento; (b) las marcas del dial van en VectorDrawables, no en 40 `ArcLine`; (c) el radio del arco se fija con el contenedor (180dp para r=84 con trazo 12), porque `ArcLine` no tiene radio; (d) la píldora se posiciona con `Transformation.translationY(63dp)` desde el centro del Box; (e) el panel de detalle del diseño (micro-drawer) no existe en un tile: la píldora abre la activity de detalle que ya existe.

L4 — Evidencia de entorno: `adb devices` vacío y `adb connect 192.168.0.15:38557` rechazado. La verificación de esta sesión es estructural (tests de contrato + build); la visual queda pendiente y se reporta como tal, no se inventa.

L5 — Línea base: `./gradlew :wear:testDebugUnitTest` verde (exit 0) antes de tocar nada.
