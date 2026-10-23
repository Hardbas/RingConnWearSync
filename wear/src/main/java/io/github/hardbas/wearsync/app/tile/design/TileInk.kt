package io.github.hardbas.wearsync.app.tile.design

import kotlin.math.roundToInt

/**
 * Every flat colour a tile is allowed to paint.
 *
 * The "Editorial Cálida" surface palette: a near-black OLED face (`#080807`),
 * warm off-white ink (`#F5F2EB`) and a solid charcoal pill (`#262522`) held down
 * by a warm hairline. Metric colours do not live here — they live in
 * [TileAccents] as ramps, because a tile may only paint an accent that belongs
 * to its own metric.
 */
object TileInk {

    /** The face of the watch: black enough to disappear into the bezel. */
    const val BASE = 0xFF080807.toInt()

    /** Solid surface of the bottom action pill. */
    const val SURFACE = 0xFF262522.toInt()

    /** Hairline that keeps the pill from floating on black. */
    const val SURFACE_BORDER = 0xFF4A4742.toInt()

    /** Hero numbers, and the numbers inside the pill. */
    const val PRIMARY = 0xFFF5F2EB.toInt()

    /** Subtitles and units: one step quieter than the hero. */
    const val SECONDARY = 0xFFB8B4AA.toInt()

    /** No-data and stale accents: the warm palette switched off. */
    const val DIM = 0xFF6E6A62.toInt()

    /** Dial ticks and separators inside the pill. */
    const val HAIRLINE = 0x2EF5F2EB.toInt()

    /** Body of the pearl marker that rides the dial. */
    const val PEARL = 0xFFFAF6EE.toInt()

    /** Alpha the bezel track carries, as a fraction of its metric accent. */
    const val TRACK_ALPHA = 0.16f

    /** Alpha a stale tile keeps on its hero, straight from the design's `.stale` rule. */
    const val STALE_HERO_ALPHA = 0.55f

    /** The bezel track: the metric accent at 16 % over black. */
    fun track(accent: Int): Int = withAlpha(accent, TRACK_ALPHA)

    /** Same colour, different alpha. Keeps every ramp decision in the palette. */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
