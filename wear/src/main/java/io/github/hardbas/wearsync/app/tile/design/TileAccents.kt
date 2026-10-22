package io.github.hardbas.wearsync.app.tile.design

import kotlin.math.roundToInt

/**
 * The metric ramps of the warm editorial dial.
 *
 * A ramp always runs from its calmest stop to its most intense one. Tiles index
 * into it; they never write a hex, and they never reach for another metric's
 * ramp. Values are the exact ARGB stops of the design render (`PALETTES.warm`).
 */
object TileAccents {

    /** Terracotta to coral: the heart-rate arc and its pearl. */
    val heartRate = intArrayOf(0xFFF2A68D.toInt(), 0xFFD96B4E.toInt())

    const val heartRateLabel = 0xFFF09A7E.toInt()

    /** Cerulean: arterial oxygen. */
    val spo2 = intArrayOf(0xFF93BEDB.toInt(), 0xFF5E97C4.toInt())

    const val spo2Label = 0xFF93BEDB.toInt()

    /** Sage: ring autonomy. */
    val battery = intArrayOf(0xFFAED09E.toInt(), 0xFF75A465.toInt())

    const val batteryLabel = 0xFFAED09E.toInt()

    /**
     * Sage -> olive -> ochre -> clay -> carmine. Five stops, so the stress dial
     * can be one continuous sweep instead of four discrete bands.
     */
    val stress = intArrayOf(
        0xFF75A478.toInt(),
        0xFFA8BE72.toInt(),
        0xFFE4B860.toInt(),
        0xFFDF8556.toInt(),
        0xFFC95252.toInt()
    )

    /** Sage: movement. */
    val steps = intArrayOf(0xFFA5C896.toInt(), 0xFF73A559.toInt())

    /** Ochre to clay: energy burned. */
    val kcal = intArrayOf(0xFFF0C26B.toInt(), 0xFFD97757.toInt())

    /** The one cool accent allowed on the warm dial: distance and temperature. */
    const val teal = 0xFF86C9BC.toInt()

    /** Depth -> REM -> light -> awake. */
    val sleep = intArrayOf(
        0xFF7B73B8.toInt(),
        0xFFB486C9.toInt(),
        0xFF82ADC9.toInt(),
        0xFFE3B86B.toInt()
    )

    const val sleepLabel = 0xFFB8B0E8.toInt()

    /** No-data and stale variants of a ramp: the same shape, all warmth removed. */
    fun dim(stops: IntArray): IntArray = IntArray(stops.size) { TileInk.DIM }

    /**
     * Point on a ramp, interpolated the way the design does it: evenly spaced
     * stops, linear between neighbours.
     */
    fun ramp(stops: IntArray, fraction: Float): Int {
        require(stops.isNotEmpty()) { "a ramp needs at least one stop" }
        if (stops.size == 1) return stops[0]
        val t = fraction.coerceIn(0f, 0.999f) * (stops.size - 1)
        val i = t.toInt()
        val f = t - i
        val a = stops[i]
        val b = stops[(i + 1).coerceAtMost(stops.lastIndex)]
        return blend(a, b, f)
    }

    /** Discrete step of a ramp, used for level words (calm, normal, medium, high). */
    fun step(stops: IntArray, index: Int): Int = stops[index.coerceIn(0, stops.lastIndex)]

    /** Linear mix of two ARGB colours. */
    fun blend(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
