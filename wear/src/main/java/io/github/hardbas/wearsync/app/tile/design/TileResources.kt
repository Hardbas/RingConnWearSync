package io.github.hardbas.wearsync.app.tile.design

import androidx.wear.protolayout.ResourceBuilders
import io.github.hardbas.wearsync.app.R

/**
 * The drawables every tile shares, exposed once.
 *
 * ProtoLayout only paints images it has been handed by id, so each tile's
 * `buildResources` has to carry the same mappings. Keeping the ids and the
 * resource lookup here is what stops five files from drifting apart.
 */
object TileResources {

    /** Radial warm halo behind the dial. */
    const val GLOW = "tile_warm_glow"

    /** Dial ticks: pulse and stress share the wide ring. */
    const val TICKS_R84 = "tile_ticks_r84"

    /** Dial ticks for the two-ring tiles. */
    const val TICKS_R78 = "tile_ticks_r78"

    /** Dial ticks for sleep. */
    const val TICKS_R74 = "tile_ticks_r74"

    /** Dial ticks plus the 30/60/80 cut marks of the stress scale. */
    const val TICKS_STRESS = "tile_ticks_stress"

    /** Sleep's fixed starfield. */
    const val STARS = "tile_stars"

    /** The crescent in front of the sleep quality word. */
    const val MOON = "tile_moon_badge"

    /**
     * The light streak every tile rotates around its bezel: the arc and its fade
     * are baked into the drawable, the tile only tints and spins it.
     */
    const val SWEEP = "tile_sweep"

    private val shared: List<Pair<String, Int>> = listOf(
        GLOW to R.drawable.tile_warm_glow,
        TICKS_R84 to R.drawable.tile_ticks_r84,
        TICKS_R78 to R.drawable.tile_ticks_r78,
        TICKS_R74 to R.drawable.tile_ticks_r74,
        TICKS_STRESS to R.drawable.tile_ticks_stress,
        STARS to R.drawable.tile_stars,
        MOON to R.drawable.tile_moon_badge,
        SWEEP to R.drawable.tile_sweep
    )

    /** Everything the warm dial needs, on top of whatever a tile adds itself. */
    fun addTo(builder: ResourceBuilders.Resources.Builder): ResourceBuilders.Resources.Builder {
        shared.forEach { (id, drawable) ->
            builder.addIdToImageMapping(
                id,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawable)
                            .build()
                    )
                    .build()
            )
        }
        return builder
    }
}
