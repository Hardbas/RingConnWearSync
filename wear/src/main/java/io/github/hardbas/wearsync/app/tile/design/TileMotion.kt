package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.expression.AnimationParameterBuilders

/**
 * Two animators per tile, never three.
 *
 * Wear OS renders tiles in SysUI with a quota-aware animator pool, so the budget
 * is not a style preference: a third animator starts dropping frames. The
 * vocabulary is therefore fixed:
 *  1. an entrance — the value arc grows from zero to its reading;
 *  2. an ambient loop — the band behind the dial breathing in opacity.
 *
 * Only these two mechanisms are used because only these two have been measured
 * animating on the target watch (SM-L500). A dynamic container rotation was
 * tried and removed: tiles rendered still.
 *
 * Both degrade gracefully: the static value of every animated property is the
 * *final* one, so a renderer that ignores the animation shows the settled dial.
 */
object TileMotion {

    /** Hard ceiling, enforced by the design contract test. */
    const val MAX_ANIMATORS = 2

    const val ENTRANCE_MS = 900L

    const val HALO_MS = 2600L

    /**
     * Breathing band of the band behind the dial. Wider than the design's
     * 0.10-0.32 on purpose: measured on the watch, a narrow band on a thin line
     * moves less than one luminance step and the tile reads as frozen.
     */
    const val HALO_MIN = 0.15f
    const val HALO_MAX = 0.55f

    /** Dial settle: long expo-out, so the arc arrives and stops. */
    fun entrance(): AnimationParameterBuilders.AnimationSpec = oneShot(ENTRANCE_MS)

    /** Halo breath: slow sine in, slow sine out, forever. */
    fun halo(): AnimationParameterBuilders.AnimationSpec = loop(HALO_MS)

    private fun oneShot(durationMs: Long): AnimationParameterBuilders.AnimationSpec =
        AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(
                AnimationParameterBuilders.AnimationParameters.Builder()
                    .setDurationMillis(durationMs)
                    .setEasing(
                        AnimationParameterBuilders.Easing.cubicBezier(0.05f, 0.7f, 0.1f, 1f)
                    )
                    .build()
            )
            .build()

    private fun loop(durationMs: Long): AnimationParameterBuilders.AnimationSpec =
        AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(
                AnimationParameterBuilders.AnimationParameters.Builder()
                    .setDurationMillis(durationMs)
                    .setEasing(
                        AnimationParameterBuilders.Easing.cubicBezier(0.37f, 0f, 0.63f, 1f)
                    )
                    .build()
            )
            .setRepeatable(AnimationParameterBuilders.Repeatable.INFINITE_REPEATABLE_WITH_REVERSE)
            .build()
}
