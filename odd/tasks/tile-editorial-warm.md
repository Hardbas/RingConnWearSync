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

- T1 — Fundación: `TileInk`/`TileAccents`/`TileType`/`TileGeometry`/`TileMotion`/`TileDial`/`TileShell`/`TileResources`, siete drawables (`tile_warm_glow`, `tile_ticks_r84/r78/r74/stress`, `tile_stars`, `tile_moon_badge`), `TileTestKit` y `TileEditorialContractTest`. Commit `965af31`.
- T2 — Tile de pulso + contrato. Commit `00c7d00`.
- T3 — Tile de vitales + contrato. Commit `83866ac`.
- T4 — Tile de estrés + contrato. Commit `389239a`.
- T5 — Tile de pasos + contrato (vuelve la costura `TileStrings`). Commit `a16c969`.
- T6 — Tile de sueño + contrato + baja de los cuatro drawables del atardecer. Commit `d0eb18c`.
- T7 — Estado «viejo» sin animadores en los cinco. Commit `21266cc`.
- T8 — Baja del sistema de diseño de primera generación (`TileDesign`, `TileGauge`, `TileChrome`, `TileMeter`, `TileDesignContractTest`) + este documento. Verificación final.

Corte de revisión: la rama ya acumulaba historial de diseño antes de esta feature; el corte de esta entrega es `965af31..HEAD` (T1-T8), 24 commits. No se apila con el resto del historial porque el sistema anterior se borra en T8.

## Log

L1 — «en la carpeta de desing tienes el nuevo diseno D:\Users\jeiss\ringconn_re\RingConnWearSync\design\_render RingConnWearSync · Edición Claude Design (1) que quiero implemetnar»

L2 — Lectura del HTML de diseño (60 514 bytes). Aporta: paletas `warm`/`contrast`, `STATE` con `live`/`stale`/`empty`, movimiento `Esencial`/`Expresivo`, y por tile el árbol exacto (anillos, radios, trazos, perla, muescas, ticks, héroe por tamaño). Se implementa `warm` + `Esencial`; `contrast` y `Expresivo` no tienen interruptor en el reloj y `Expresivo` no es expresable en ProtoLayout (cifras que cuentan, sheen de texto, estrellas fugaces).

L3 — Decisiones de traducción a ProtoLayout: (a) el resplandor radial no existe como brush (1.3.0 sólo tiene `LinearGradient` y `SweepGradient`), va como `Image` de un VectorDrawable con gradiente radial, tintado con el acento; (b) las marcas del dial van en VectorDrawables, no en 40 `ArcLine`; (c) el radio del arco se fija con el contenedor (180dp para r=84 con trazo 12), porque `ArcLine` no tiene radio; (d) la píldora se posiciona con `Transformation.translationY(63dp)` desde el centro del Box; (e) el panel de detalle del diseño (micro-drawer) no existe en un tile: la píldora abre la activity de detalle que ya existe.

L4 — Evidencia de entorno: `adb devices` vacío y `adb connect 192.168.0.15:38557` rechazado. La verificación de esta sesión es estructural (tests de contrato + build); la visual queda pendiente y se reporta como tal, no se inventa.

L5 — Línea base: `./gradlew :wear:testDebugUnitTest` verde (exit 0) antes de tocar nada.

L6 — Reparto del trabajo: fundación y tile de referencia escritos en la sesión principal; vitales/estrés y pasos/sueño migrados por dos workers acotados con superficies de edición explícitas. Los workers no commitean: cada tile se commitea solo después de correr la suite completa.

L7 — Hueco del sistema detectado por el worker de pasos: `TileDial.rings()` no tenía interruptor de halo, así que un tile «viejo» no podía quedar en cero animadores sin dibujar arcos a mano. Se agregó `halo`/`entrance` a `value()` y `rings()`, se borró el helper privado del tile y los tres tiles restantes usan la misma regla.

L8 — Verificación: `:wear:testDebugUnitTest` = 88 tests verdes (17 de contrato de diseño + 71 de tile y auditorías de fuente); `:wear:assembleDebug` verde. Cada commit de tile se verificó con la suite completa antes de commitear.

L9 — Sin verificación visual: `adb devices` vacío y `adb connect 192.168.0.15:38557` sin respuesta durante toda la sesión. Queda sin confirmar en el SM-L500: (a) que el arco se dibuje con el radio que da el contenedor (180dp → r=84), (b) que el `ColorFilter` del resplandor respete el alfa del gradiente radial (si no, el halo sale blanco), (c) la legibilidad real del héroe de 50sp y de la píldora a +63dp. Nada de eso se reporta como verificado.

L10 — Alcance no implementado del render de diseño, por imposibilidad del motor: los modos `Expresivo` y `Alto Contraste` (sin interruptor en el reloj y sin brush radial, sheen de texto ni cifras que cuenten en ProtoLayout 1.3.0) y el micro-drawer dentro del tile (un tile solo puede lanzar una activity; la píldora abre la pantalla de detalle que ya existía).
