package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.expression.AnimationParameterBuilders

/**
 * Two animators per tile, never three.
 *
 * Wear OS renders tiles in SysUI with a quota-aware animator pool, so the budget
 * is not a style preference: a third animator starts dropping frames.
 *
 * Every spec here uses a mechanism that has been measured animating on the
 * target watch (SM-L500). Two earlier attempts failed on hardware and are gone:
 * a dynamic **container rotation** (rendered still) and a narrow opacity band on
 * a thin line (moved less than one luminance step). What is left:
 *
 *  1. `ArcLine` opacity — the bezel breathing at 0.45..0.95, the exact band and
 *     period measured working in production;
 *  2. `Image` translation, `ArcLine` length and `Image` opacity — the expressive
 *     backdrop layers, which are what the design render shows in its
 *     "Expresivo" mode.
 *
 * The static value of every animated property is the one the renderer falls back
 * to if it ignores the animation, so a mute renderer still shows a settled dial.
 */
object TileMotion {

    /** Hard ceiling, enforced by the design contract test. */
    const val MAX_ANIMATORS = 2

    /**
     * Bezel breath. Values copied from the production build that was measured on
     * the watch: a 0.45-0.95 band on the 16 % track moved two luminance steps,
     * while 0.16-0.34 moved 0.6 and read as frozen.
     */
    const val AMBIENT_MS = 3400L
    const val AMBIENT_MIN = 0.45f
    const val AMBIENT_MAX = 0.95f

    /** Linear travel used by a scrolling backdrop (the ECG strip). */
    const val SCROLL_MS = 3000L

    /** Long, soft drift used by the glowing blobs. */
    const val DRIFT_MS = 9000L

    /** One pass of a scanning arc (vitals, steps). */
    const val SCAN_MS = 4200L

    /** Slow breath of a radial glow. */
    const val PULSE_MS = 7000L

    /** Bezel breath: slow sine in, slow sine out, for ever. */
    fun ambient(): AnimationParameterBuilders.AnimationSpec = loop(AMBIENT_MS, EASING)

    /** Backdrop travel that restarts from the origin, so a repeated strip looks continuous. */
    fun scroll(durationMs: Long = SCROLL_MS): AnimationParameterBuilders.AnimationSpec =
        restart(durationMs)

    /** Backdrop travel that goes back and forth, so nothing ever snaps. */
    fun drift(durationMs: Long = DRIFT_MS): AnimationParameterBuilders.AnimationSpec =
        loop(durationMs, EASING)

    /** An arc that sweeps and starts over. */
    fun scan(durationMs: Long = SCAN_MS): AnimationParameterBuilders.AnimationSpec =
        restart(durationMs)

    /** A radial glow that breathes. */
    fun pulse(durationMs: Long = PULSE_MS): AnimationParameterBuilders.AnimationSpec =
        loop(durationMs, EASING)

    private fun loop(
        durationMs: Long,
        easing: AnimationParameterBuilders.Easing
    ): AnimationParameterBuilders.AnimationSpec =
        AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(parameters(durationMs, easing))
            .setRepeatable(AnimationParameterBuilders.Repeatable.INFINITE_REPEATABLE_WITH_REVERSE)
            .build()

    private fun restart(durationMs: Long): AnimationParameterBuilders.AnimationSpec =
        AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(parameters(durationMs, LINEAR))
            .setRepeatable(AnimationParameterBuilders.Repeatable.INFINITE_REPEATABLE_WITH_RESTART)
            .build()

    private fun parameters(
        durationMs: Long,
        easing: AnimationParameterBuilders.Easing
    ): AnimationParameterBuilders.AnimationParameters =
        AnimationParameterBuilders.AnimationParameters.Builder()
            .setDurationMillis(durationMs)
            .setEasing(easing)
            .build()

    /** The slow-in, slow-out curve the design uses for everything that breathes. */
    private val EASING: AnimationParameterBuilders.Easing =
        AnimationParameterBuilders.Easing.cubicBezier(0.37f, 0f, 0.63f, 1f)

    /** A cubic Bezier with both control points on the diagonal is a straight line. */
    private val LINEAR: AnimationParameterBuilders.Easing =
        AnimationParameterBuilders.Easing.cubicBezier(0f, 0f, 1f, 1f)
}
