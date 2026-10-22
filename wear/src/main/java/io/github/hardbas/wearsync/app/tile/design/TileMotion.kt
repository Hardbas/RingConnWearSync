package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.expression.AnimationParameterBuilders

/**
 * The motion vocabulary, limited to what a probe tile measured animating on the
 * target watch (SM-L500).
 *
 * The probe carried a known-good control in the same tree, so a property that
 * came out still is a fact about the mechanism and not about the watch. What the
 * measurement says, and what this file is allowed to use:
 *
 *  - **Animates**: `Transformation` rotation, `Transformation` scale,
 *    `Transformation` translation, `Modifiers.opacity`, `ArcLine` length,
 *    `ArcLine` opacity and `Arc.anchorAngle`.
 *  - **Does not animate**: anything dynamic inside a `Brush` — `ColorStop`
 *    offsets and the `SweepGradient` start/end angle — because the brush is
 *    snapshotted when the tile is built; and rotation on a container. Both were
 *    measured still, so neither appears below.
 *
 * An earlier version of this file also claimed a hard budget of two animators
 * per tile and shapes its vocabulary around it. The measurement says that claim
 * was false: the renderer runs four animated nodes on the pulse tile without
 * dropping frames. There is no budget constant any more; each tile documents the
 * nodes it spends instead.
 *
 * With that map, the gestures are:
 *  - [sweep] spins a light streak baked into a 192dp drawable around the bezel,
 *    pivot on the tile centre, so the light travels the rim and never enters the
 *    face;
 *  - [beat] drives the pearl bloom: one round trip is one heartbeat, so the
 *    bloom brightens and grows at the wearer's rate;
 *  - [ambient] is the bezel breath and [pulse] the slow breath of a radial halo;
 *  - [swell] is the bloom's own growth curve, for a bloom with no pulse behind
 *    it.
 *
 * The static value of every animated property is the one a renderer falls back
 * to if it ignores the animation, so a mute renderer still shows a settled dial.
 */
object TileMotion {

    /**
     * Bezel breath. Values copied from the production build that was measured on
     * the watch: a 0.45-0.95 band on the 16 % track moved two luminance steps,
     * while 0.16-0.34 moved 0.6 and read as frozen.
     */
    const val AMBIENT_MS = 3400L
    const val AMBIENT_MIN = 0.45f
    const val AMBIENT_MAX = 0.95f

    /** One full turn of the light streak, per tile. */
    const val SWEEP_MS_PULSE = 14_000L
    const val SWEEP_MS_RINGS = 18_000L
    const val SWEEP_MS_STRESS = 20_000L
    const val SWEEP_MS_SLEEP = 40_000L

    /** Bounds of one leg of a heartbeat: 136 bpm at the fast end, 33 at the slow. */
    const val BEAT_MIN_MS = 220L
    const val BEAT_MAX_MS = 900L

    /** Slow breath of a radial glow. */
    const val PULSE_MS = 7000L

    /** The bloom's growth when it is not tied to a heart rate. */
    const val SWELL_MS = 1600L

    /** Bezel breath: slow sine in, slow sine out, for ever. */
    fun ambient(): AnimationParameterBuilders.AnimationSpec = loop(AMBIENT_MS, EASING)

    /**
     * One full, seamless turn of the baked light streak.
     *
     * Linear and restarting: a rotation is symmetric under a full turn, so the
     * restart is invisible and the light never appears to reverse.
     */
    fun sweep(durationMs: Long): AnimationParameterBuilders.AnimationSpec = restart(durationMs)

    /**
     * One heartbeat, out and back, for ever.
     *
     * Each leg is half a beat — [beatLegMs] — so the round trip is one beat at
     * [bpm]. A missing or zero reading gets the slowest leg instead of a division
     * by zero.
     */
    fun beat(bpm: Int): AnimationParameterBuilders.AnimationSpec = loop(beatLegMs(bpm), EASING)

    /** The bloom's growth: out and back, cubic ease. */
    fun swell(durationMs: Long = SWELL_MS): AnimationParameterBuilders.AnimationSpec =
        loop(durationMs, EASING)

    /** A radial glow that breathes. */
    fun pulse(durationMs: Long = PULSE_MS): AnimationParameterBuilders.AnimationSpec =
        loop(durationMs, EASING)

    /**
     * One leg of the heartbeat in milliseconds: half a beat, clamped to
     * [BEAT_MIN_MS]..[BEAT_MAX_MS]. Faster than the floor reads as flicker;
     * slower than the ceiling reads as a stuck glow.
     */
    fun beatLegMs(bpm: Int): Long =
        if (bpm <= 0) {
            BEAT_MAX_MS
        } else {
            (60_000L / bpm / 2L).coerceIn(BEAT_MIN_MS, BEAT_MAX_MS)
        }

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
