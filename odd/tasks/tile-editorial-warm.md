# Editorial Cálida — the five watch tiles, second redesign

Feature branch: `feat/tile-design-system`. Source of truth: `design/_render/RingConnWearSync · Edición Claude Design (1).html` (five 192dp tiles, palette `warm`, motion `Esencial`). Scope: `wear/src/main/java/io/github/hardbas/wearsync/app/tile/**`, `wear/src/main/res/drawable/tile_*.xml`, `wear/src/test/java/io/github/hardbas/wearsync/app/tile/**`. Out of scope: complications, detail activities, phone module, data model, `strings.xml` content (existing keys only; Spanish comes from `values-es`).

## Specs

S1 — **Paleta única y cálida.** Ningún `*TileService.kt` contiene literales `0x……` de color: todo sale de `TileDesign`. Los únicos valores son la rampa cálida del diseño (`pulse #F2A68D→#D96B4E`, `spo2 #93BEDB→#5E97C4`, `batt #AED09E→#75A465`, `spec #75A478/#A8BE72/#E4B860/#DF8556/#C95252`, `steps #A5C896→#73A559`, `kcal #F0C26B→#D97757`, `teal #86C9BC`, `sleep #7B73B8/#B486C9/#82ADC9/#E3B86B`), el fondo `#080807`, el texto `#F5F2EB` / `#B8B4AA` y la píldora `#262522` con borde `#4A4742`. Los degradados de fondo por métrica desaparecen: el fondo es negro OLED con un resplandor radial del acento al 22%.

S2 — **Jerarquía tipográfica del diseño.** Héroe: 50sp (44sp en Vitales, 38sp en Pasos), peso 500, `tabularNum()`, tracking −0.03em, color `#F5F2EB`. Etiqueta: 10.5sp peso 600, tracking +0.09em, mayúsculas (Sueño la mantiene en caja alta/baja a 12sp, tracking +0.02em, como el diseño). Subtítulo: 12sp peso 500, `#B8B4AA`. Píldora: 10sp tabular. Héroe/etiqueta ≥ 3.0.

S3 — **Geometría del bisel.** Un solo arco de 260° que arranca en 140°, radio 84dp (contenedor de 180dp), trazo 12dp, puntas redondeadas. Anillos concéntricos cuando el tile lleva dos lecturas: exterior radio 85 (trazo 10.5 Vitales / 11.5 Pasos) e interior radio 71 (contenedor 152dp). La píldora mide 28dp de alto, radio 14, centro 63dp debajo del centro del tile. El resplandor radial ocupa 156dp (inset 18dp).

S4 — **Animación medida, no adivinada.** Cada tile usa sólo gestos verificados en el SM-L500 con una sonda que incluye un control de una palanca ya conocida en el mismo árbol. Lo que anima: `Transformation` (rotación, escala, traslación), `Modifiers.opacity`, `ArcLine` (largo y opacidad) y `Arc.anchorAngle`. Lo que **no** anima y queda prohibido: cualquier dinámica dentro de un `Brush` (paradas de `ColorStop`, `startAngle`/`endAngle` del `SweepGradient`), la rotación de un contenedor, y cualquier forma que cruce la esfera o pase por detrás de un número.

Los gestos permitidos, con sus números:
1. **Latido del pulso**: `Image` del resplandor sobre la perla, opacidad 0.05→0.72 y escala 0.55→1.15, ida y vuelta; duración de cada tramo = `60 000 / bpm / 2` ms (una ida y vuelta es un latido), acotada a 220..900 ms.
2. **Estela de luz**: `Image` de 192dp con la estela horneada en el radio 84, rotación lineal 0→360 sin reversa. 14 s en Pulso, 18 s en Vitales y Pasos, 20 s en Estrés, 40 s en Sueño. Opacidad 0.35 (Sueño) a 0.50 (Pulso).
3. **Respiración del bisel**: opacidad de `ArcLine` 0.45→0.95 en 3400 ms con reversa (la banda medida en producción).
4. **Respiración del resplandor** (Estrés): opacidad de la `Image` del halo 0.08→0.40 en 4500 ms con reversa.

Ningún animador toca el héroe. Sin datos o en estado viejo: cero animadores.

S11 — **Estela (`tile_sweep.xml`).** Vector 192×192 blanco con el alfa horneado: tres trazos de arco concéntricos sobre el radio 84 (12dp al 10%, 7dp al 22%, 3.5dp al 60%), cada uno con degradé de alfa a lo largo del trazo (0 en ambas puntas, máximo al centro) y 46° de longitud. Blanco para que cada tile lo tiña con su acento. El pivote de la rotación es el centro del tile, así que la luz recorre el canto y nunca entra a la esfera.

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
- T9 — Motor de movimiento medido: `TileMotion` (latido, estela, respiración, oleaje) y `TileAmbient` (estela rotada, bloom posicionado con traslación + escala), `tile_sweep.xml`, baja de `tile_ecg.xml` y `tile_meteor.xml`. Commit `32a250e`.
- T10 — Pulso: fuera el ECG; bloom que late a 60/bpm sobre la perla; estela 14 s. Commit `32a250e`.
- T11 — Vitales y Pasos: fuera el arco que barre; estela 18 s. Commit `32a250e`.
- T12 — Estrés y Sueño: fuera la estrella fugaz; estela 20 s y 40 s. Commit `32a250e`.

Corte de revisión: la rama ya acumulaba historial de diseño antes de esta feature. Se entrega en dos rebanadas: `965af31..644c701` (T1-T8) y `32a250e` (T9-T12, motor de movimiento medido), que es la que hay que revisar contra la anterior. No se apila con el resto del historial porque el sistema anterior se borra en T8.

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

L11 — Bug reportado en el reloj con 1.9.17-warm1: «ningún tile tiene animación». Causa: la entrada era una rotación dinámica de contenedor (`Transformation.setRotation` con `DynamicFloat.animate`), un mecanismo que nunca se midió en el SM-L500, y el loop infinito era un halo de 4dp de ancho con banda 0.10–0.32, la misma lección que L10 del documento anterior ya había registrado como invisible. Corrección: se descarta la rotación y se vuelve a los dos únicos mecanismos medidos en este reloj — barrido de longitud de `ArcLine` (la vieja entrada del arco) y opacidad de `ArcLine` (la vieja respiración del bisel) — con banda 0.15–0.55. El resplandor radial queda estático y Sueño estrena una banda única respirando detrás de las cuatro fases. Tokens muertos (`ENTRANCE_FROM_DEGREES`, `HALO_STATIC_ALPHA`, `SLEEP_*`, `sleepGlow()`) borrados.

L12 — Segundo reporte de hardware con 1.9.17-warm2: «no veo release version la ultima fue hace 11 horas» (publicación) y «ningún tile tiene animación». Con warm2 tampoco se movía nada. Diagnóstico definitivo: los dos únicos mecanismos que este reloj animó alguna vez son el largo de `ArcLine` y la opacidad de `ArcLine` con banda ancha; la rotación de contenedor nunca funcionó y la banda 0.15–0.55 sobre una franja de 4dp tampoco se ve. Corrección (warm3): la entrada por barrido desaparece, la respiración se muda al track visible (0.45→0.95 en 3400ms, los valores exactos medidos en producción) y el segundo animador pasa a una capa de fondo expresiva por tile (`TileAmbient`), que es lo que el render muestra en su modo por defecto. Verificado: 76 tests, `assembleDebug` y `assembleRelease`.

L13 — «las animaciones de los tiles estan horribles... parecen imagenes 2D animadas... y el del heart beat es horrible». Diagnóstico sobre el código y el render: el ECG era una silueta **rellena** de 384×192 que se trasladaba a 64 dp/s por detrás del número; el arco que barre era un spinner de carga literal (largo 0→260° con restart); la estrella cruzaba la esfera. Nada tenía relación con la medición. Decisión del usuario: «sólo la luz se mueve» — el pulso late a su intervalo, nada se desplaza por la esfera.

L14 — Sonda de medición en el SM-L500 (tres builds descartables, control dentro del mismo tile). Resultado: **animan** rotación de `Image`, escala de `Image` y `anchorAngle` del `Arc`; **no anima** ninguna dinámica dentro de un `Brush` (offsets de `ColorStop`, `endAngle` del `SweepGradient`): 0,00 de diferencia en 28/28 pares de frames con el control latiendo 25/28 en el mismo árbol. Corolario: el techo de «2 animadores por tile» que gobernaba S4 desde la primera sesión **era una inferencia falsa** — se vieron cuatro nodos animando a la vez. El barrido de luz sale de rotar una `Image`, no del degradado. Decisión del usuario: estela en los cinco tiles.

L15 — Instalación en el reloj: los APK release se firman con el keystore debug y se actualizan con `adb install -r`, pero **no** se puede volver atrás (`INSTALL_FAILED_VERSION_DOWNGRADE`, y `-d` no lo salta porque el release no es debuggable): el `versionCode` siempre sube. El reloj apaga pantalla a los 15 s y eso tira el adb inalámbrico; medir exige despertar, navegar y verificar la captura antes de comparar.
