package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders

/**
 * Single source of visual truth for the five watch tiles.
 *
 * Every tile draws its colours, type scale, chrome geometry and motion specs
 * from here, so the carousel reads as one app instead of five separately
 * hand-painted gauges. Tiles only supply meaning: which number, which accent.
 *
 * Anything a tile needs that is not exposed here is meant to be added here
 * first, so the whole family moves together.
 */
object TileDesign {

    /** The only greys and whites allowed inside a tile. */
    object Ink {
        /** Deep base behind the per-metric gradient. */
        val BASE = 0xFF0B0D14.toInt()

        /** Bezel track: barely-there white so the rim exists without competing. */
        val TRACK = 0x1FFFFFFF.toInt()

        /** Action pill and anything that must read as a raised surface. */
        val GLASS = 0x2BFFFFFF.toInt()

        /** Badges and sub-labels: one step quieter than [GLASS]. */
        val GLASS_SUBTLE = 0x1EFFFFFF.toInt()
    }

    object Text {
        val PRIMARY = 0xFFFFFFFF.toInt()
        val SECONDARY = 0xFFB4BAC7.toInt()
        val MUTED = 0xFF868D9B.toInt()
    }

    /**
     * Accent ramps. Order matters: each ramp runs from the calmest state to the
     * most intense one, and tiles index into it instead of picking a hex.
     */
    object Accent {
        /** Z1 Resting -> Z5 Peak. */
        val heartRate = intArrayOf(
            0xFF47CDFF.toInt(),
            0xFF8FE34A.toInt(),
            0xFFFFB703.toInt(),
            0xFFFB8500.toInt(),
            0xFFFF5D99.toInt()
        )

        val vitals = 0xFFEDCB8B.toInt()
        val spo2 = 0xFF47CDFF.toInt()

        /** Relaxed -> Balanced -> Moderate -> High. */
        val stress = intArrayOf(
            0xFF47CDFF.toInt(),
            0xFF7ED957.toInt(),
            0xFFFFB703.toInt(),
            0xFFFF5D99.toInt()
        )

        val activity = 0xFFC6F432.toInt()

        /** Excellent -> Good -> Fair. */
        val sleep = intArrayOf(
            0xFF47CDFF.toInt(),
            0xFF7C8CF8.toInt(),
            0xFFFFD166.toInt()
        )
        val sleepLavender = 0xFFA5AEDE.toInt()
        val starlight = 0xFFFFD166.toInt()

        /** Skin temperature states: neutral -> warm departure -> cool departure. */
        val stable = 0xFF00E676.toInt()
        val warm = 0xFFFF7043.toInt()
        val cool = 0xFF38BDF8.toInt()
    }

    /**
     * One hero per tile, one label, one value. The 40sp hero is what gives the
     * tile a focal point on One UI Watch; 30sp is a secondary read there.
     */
    object Type {
        const val HERO_SIZE = 40f
        const val LABEL_SIZE = 11f
        const val VALUE_SIZE = 11.5f
        const val CAPTION_SIZE = 10f
        const val UNIT_SIZE = 12f

        private const val HERO_TRACKING = -0.02f
        private const val LABEL_TRACKING = 0.08f

        /** Hero number: big, tabular so digits never jitter, optically tightened. */
        fun hero(color: Int = Text.PRIMARY): LayoutElementBuilders.FontStyle =
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(HERO_SIZE))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                .setColor(argb(color))
                .setLetterSpacing(DimensionBuilders.em(HERO_TRACKING))
                .setSettings(LayoutElementBuilders.FontSetting.tabularNum())
                .build()

        /** Section label: uppercase, tracked out. */
        fun label(color: Int): LayoutElementBuilders.FontStyle =
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(LABEL_SIZE))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                .setColor(argb(color))
                .setLetterSpacing(DimensionBuilders.em(LABEL_TRACKING))
                .build()

        /** Secondary read: still tabular, one step down from the hero. */
        fun value(color: Int): LayoutElementBuilders.FontStyle =
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(VALUE_SIZE))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                .setColor(argb(color))
                .setSettings(LayoutElementBuilders.FontSetting.tabularNum())
                .build()

        /** Footer and badge copy. */
        fun caption(color: Int): LayoutElementBuilders.FontStyle =
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(CAPTION_SIZE))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                .setColor(argb(color))
                .setSettings(LayoutElementBuilders.FontSetting.tabularNum())
                .build()

        /** The unit that rides next to the hero. */
        fun unit(color: Int): LayoutElementBuilders.FontStyle =
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(UNIT_SIZE))
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                .setColor(argb(color))
                .build()
    }

    /** Shared geometry, so no tile invents its own pill. */
    object Chrome {
        const val ACTION_WIDTH = 88f
        const val ACTION_HEIGHT = 28f
        const val ACTION_RADIUS = 14f

        const val BADGE_HEIGHT = 18f
        const val BADGE_RADIUS = 9f
        const val BADGE_PADDING = 7f

        const val ARC_THICKNESS = 6.5f
        const val ARC_SPAN = 260f
        const val ARC_START = 140f

        /** Bezel swatch used by the per-metric ladder under the hero. */
        const val LADDER_TRACK_HEIGHT = 3f
        const val LADDER_TRACK_RADIUS = 1.5f
        const val LADDER_WIDTH = 84f
        const val LADDER_GAP = 3f
    }

    /**
     * Motion vocabulary. Two gestures per tile, never more: one entrance and
     * one ambient breath. Both are eased settles, never bounces.
     */
    object Motion {
        /** Hard ceiling enforced by the tile contract test. */
        const val MAX_ANIMATORS = 2

        /** Arc entrance: long expo-out so the gauge settles instead of stopping dead. */
        fun entrance(): AnimationParameterBuilders.AnimationSpec =
            AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(1100L)
                        .setEasing(
                            AnimationParameterBuilders.Easing.cubicBezier(0.16f, 1.0f, 0.3f, 1.0f)
                        )
                        .build()
                )
                .build()

        /** Ambient breath: slow, shallow, reversible, so the rim lives without moving. */
        fun breathe(): AnimationParameterBuilders.AnimationSpec =
            AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(3400L)
                        .setEasing(
                            AnimationParameterBuilders.Easing.cubicBezier(0.37f, 0.0f, 0.63f, 1.0f)
                        )
                        .build()
                )
                .setRepeatable(AnimationParameterBuilders.Repeatable.INFINITE_REPEATABLE_WITH_REVERSE)
                .build()

        /** Breathing band for the bezel track. */
        const val BREATHE_MIN = 0.16f
        const val BREATHE_MAX = 0.34f
    }
}

/** Per-metric identity: which accent ramp and which backdrop gradient. */
enum class TileMetric(val gradient: IntArray) {
    HEART_RATE(TileBackdrop.heartRate),
    VITALS(TileBackdrop.vitals),
    STRESS(TileBackdrop.stress),
    ACTIVITY(TileBackdrop.activity),
    SLEEP(TileBackdrop.sleep)
}

/**
 * Full-bleed backdrop stops, deep and low-chroma so the hero number keeps its
 * contrast. Three stops: a lifted corner, a mid tone, then the shared base.
 * These live next to the palette on purpose — the wireframe used to duplicate
 * its colours into separate vector drawables.
 */
object TileBackdrop {
    val heartRate = intArrayOf(0xFF3B1430.toInt(), 0xFF200F1F.toInt(), 0xFF0B0D14.toInt())
    val vitals = intArrayOf(0xFF352A1A.toInt(), 0xFF1C1710.toInt(), 0xFF0B0D14.toInt())
    val stress = intArrayOf(0xFF12303D.toInt(), 0xFF0D1B24.toInt(), 0xFF0B0D14.toInt())
    val activity = intArrayOf(0xFF2A3314.toInt(), 0xFF171C0F.toInt(), 0xFF0B0D14.toInt())
    val sleep = intArrayOf(0xFF22294F.toInt(), 0xFF111634.toInt(), 0xFF06070F.toInt())
}
