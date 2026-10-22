package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.expression.AnimationParameterBuilders

/**
 * Two animators per tile, never three.
 *
 * Wear OS renders tiles in SysUI with a quota-aware animator pool, so the budget
 * is not a style preference: a third animator starts dropping frames. The
 * vocabulary is therefore fixed:
 *  1. an entrance — the value arc settles into place from -25 degrees;
 *  2. an ambient loop — either the arc halo breathing, or the sleep radial glow.
 *
 * Both degrade gracefully: the static value of every animated property is the
 * *final* one, so a renderer that ignores the animation shows the settled dial.
 */
object TileMotion {

    /** Hard ceiling, enforced by the design contract test. */
    const val MAX_ANIMATORS = 2

    const val ENTRANCE_MS = 900L
    const val ENTRANCE_FROM_DEGREES = -25f

    const val HALO_MS = 2600L
    const val HALO_MIN = 0.10f
    const val HALO_MAX = 0.32f

    const val SLEEP_MS = 5500L
    const val SLEEP_MIN = 0.35f
    const val SLEEP_MAX = 0.60f

    /** Dial settle: long expo-out, so the arc arrives and stops. */
    fun entrance(): AnimationParameterBuilders.AnimationSpec = oneShot(ENTRANCE_MS)

    /** Halo breath: slow sine in, slow sine out, forever. */
    fun halo(): AnimationParameterBuilders.AnimationSpec = loop(HALO_MS)

    /** Sleep's radial glow: the longest loop of the five, so it never draws attention. */
    fun sleepGlow(): AnimationParameterBuilders.AnimationSpec = loop(SLEEP_MS)

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
