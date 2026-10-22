# Brief: design five Wear OS tiles for RingConnWearSync

You are designing the five tiles of a Wear OS app. Output production-ready designs, not mood boards. Every number you give will be typed into code.

## The product

Two-module Android app for people who wear a smart ring and a smartwatch:

- **mobile**: phone bridge that pulls telemetry from the RingConn companion app (ring: RingConn Gen 2 Air) and pushes it to the watch over the Wearable Data Layer.
- **wear**: standalone Wear OS app with 5 tiles, complications, and detail screens.

The ring cannot read pulse through tattooed skin reliably, so the watch is where the user actually looks. The tiles are the primary surface: the user swipes the carousel dozens of times a day.

Stack: `androidx.wear.protolayout:protolayout:1.3.0`, `protolayout-expression:1.3.0`, `androidx.wear.tiles:tiles:1.4.1`.

## Target device

- Samsung Galaxy Watch 8 Classic (SM-L500), One UI Watch 8, Wear OS 6 / Android 16.
- 438×438 px at 340 dpi = **192 dp logical**, full circle (`LARGE_ROUND`).
- Tiles are full-bleed; the layout root gets the whole circle.

## How tiles actually render (do not fight this)

These are measured facts on this exact watch, not documentation guesses.

1. **Remote rendering.** `ProtoTilesRelayService` renders tiles in SysUI. There is no Android `View`, no Compose, no `Canvas`, no `AnimatedVectorDrawable`, no Lottie, no custom drawing. You emit ProtoLayout builders; SysUI paints them. Vector drawables are only usable as `Image` resources.
2. **Elements available:** `Box`, `Column`, `Row`, `Text`, `Image` (by resource id), `Spacer`, `Arc` + `ArcLine`. That is the whole vocabulary.
3. **Modifiers per element:** background (flat `ColorProp`; since 1.3.0 also a `Brush`), corner radius, border, padding, clickable (launches an activity), opacity (`FloatProp`), transformation (translationX/Y, scaleX/Y, rotation, pivot), and a content-update fade.
4. **Animatable properties, via `DynamicBuilders` only:** arc length (`DegreesProp`), dp/sp dimensions, opacity, translation, scale, rotation, `ColorProp`, and state-keyed values. Nothing else moves.
5. **Animator budget: about two concurrent animators per tile** (`QuotaAwareAnimator`). A third risks dropped frames.
6. **A one-shot animation ends and the tile freezes forever after it.** If you want a tile that feels alive, one of the two animators must be an infinite reversible loop (`Repeatable.INFINITE_REPEATABLE_WITH_REVERSE`). Off-screen preload also skips one-shots straight to their end value.
7. **Ambient loops need real amplitude.** A dynamic opacity band of 0.16→0.34 applied to a track at 12% alpha moved measured screen luminance by 0.6/255 — invisible in the hand. Design ambient motion to move at least ~2/255.
8. **Arc angle convention:** 0° at 3 o'clock, increasing clockwise. A 260° span anchored at 140° starts at 7:40 and ends at 4:20, leaving a 100° gap at the bottom. That is the Samsung gauge silhouette.
9. **Dynamic width crashes the renderer** (`IllegalStateException`) unless `layoutConstraintsForDynamicWidth` is supplied on that axis. Keep widths as static dp values computed at build time.
10. **`ArcLine` accepts `color` and `brush` as separate fields.** Set both: a renderer that ignores the brush then degrades to a flat colour instead of disappearing.
11. **Typography:** `FontStyle` gives size (sp), weight, letter spacing (em), font feature settings (there is a `tabularNum()` helper), and preferred families. There is no autoshrink and no ellipsis control beyond `maxLines` — text that does not fit simply overflows into the bezel.
12. **The safe circle.** A 192dp tile is a circle of radius 96dp. Usable width at a distance `y` from the centre is `2·sqrt(96² − y²)`, minus whatever inset you want from the rim. For example, a row 67dp above the centre has about 119dp of usable width. Header rows sit high, which is exactly where this budget is tightest. **This is where the last redesign broke**, so every line you place must come with its y offset and its width, and the width must be under the chord.

## Data you may use — nothing else exists

```
lastHeartRate, avgHeartRate, minHeartRate, maxHeartRate      (bpm)
lastSpo2, avgSpo2, minSleepingSpo2                            (%)
lastHrv, avgHrv                                               (ms)
sleepScore                                                    (0-100)
activityScore                                                 (0-100)
stressScore                                                   (0-100)
liveSteps, targetSteps, liveCalorie, targetCalorie
activeMinutes, standHours, distanceKm
sleepDeepMins, sleepRemMins, sleepLightMins, sleepAwakeMins, napMinutes
sleepEfficiencyPct                                            (0-100)
zone1Mins .. zone5Mins                                        (HR zone minutes)
stressDawn, stressMorning, stressAfternoon, stressNight       (0-100 each)
hrPhaseDeep, hrPhaseRem, hrPhaseLight                         (bpm per sleep phase)
skinTempOffset, skinTempBaseline                              (°C; offset from personal baseline)
respiratoryRate, odiDropsPerHour, sleepingHrDipPct
ringBattery, isCharging, isConnected, refreshTimestamp
bedtimeWindow                                                 (free text)
```

There is **no history**: no 7-day series, no hourly buckets. Anything that looks like a chart must be derived from the fields above (zones, phases, time-of-day stress, goal progress).

## The five tiles

1. **Heart rate** — live bpm, zone context, HRV.
2. **Vitals** — heart rate plus SpO2 and ring battery in one glance.
3. **Stress** — score, level, HRV.
4. **Activity / steps** — steps, goal progress, active minutes, calories, distance.
5. **Sleep** — sleep score, quality, duration, skin temperature offset.

## What this user already rejected (do not reproduce it)

- **Muted, low-chroma backdrops.** Their words: "color apagado".
- **Thin 3dp meters sitting inside a translucent frosted card.** The card shrank the graph and read as flatter than before. Their words: "me gustan las gráficas que se vean" — they want the *graph* to be the visible thing.
- **Editorial restraint**: fewer elements, more air, thinner type. Their words: "colocaste todo mas aburrido".
- **A one-shot scene animation that then leaves the tile static.** Their words: "la animación del sueño solo pasa una vez".
- **Headers that collide with the arc.** Their words: "los títulos se salen o chocan con las líneas".

## What they have liked

- The 1.9.15 activity tile: a 260° perimeter goal arc, a 30–32sp hero number, a three-colour sub-metric row (active time, kcal, distance), a goal capsule and a details pill.
- The sleep scene: starfield, a three-tone crescent moon, a sinking sun occluded by a horizon limb.
- Frosted glass as a *surface under* content — never as a wrapper that shrinks the content.
- Large tabular numbers.
- High contrast.

## Your job

Design all five tiles. Per tile deliver:

1. **Concept** — a name and one sentence explaining what makes it unmistakably *that* metric and not the same template in another colour.
2. **Wireframe** — ASCII, 192×192, with the safe circle marked and every element boxed.
3. **Token table** — every colour as ARGB hex, every font size in sp, weight and tracking in em.
4. **Element tree** — explicit dp: x/y offsets from the tile centre, width, height, alignment. Every text line comes with its estimated width and the chord budget at its y, so fit is proven, not hoped.
5. **Motion table** — property, from→to, duration, easing curve, repeat mode. End with the animator count (never above 2) and state which one is the infinite one, if any.
6. **Rationale** — why this reads as One UI Watch 8 sitting next to Samsung Health's own tiles.
7. **Kotlin** — the tile as a self-contained ProtoLayout file compiling against 1.3.0, drawing from one shared token object. Use `TileDesign`-style tokens rather than literals.

Global constraints for all five:

- Keep every reading the tile shows today; do not invent metrics.
- At most four non-spacer blocks per tile, one tappable action pill.
- One hero number per tile, at least 38sp, tabular figures.
- Labels must survive both English and Spanish, so keep them to one short word.
- Every accent colour comes from a ramp you define once; the metric accent must appear on more than the arc (the hero unit, the graph, or the badge).
- State the graph's dimensions in dp and make it the loudest element after the number. A 3dp bar is not a graph.

## Anti-goals

- No new dependencies, no new data fields, no phone-side changes.
- No `AnimatedVectorDrawable`, Lottie, Canvas, or custom drawing.
- No more than two animators per tile.
- No design that only works at 100% data (empty and stale states must be designed too).
