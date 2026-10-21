package io.github.hardbas.wearsync.app.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.AndroidTileStrings
import io.github.hardbas.wearsync.app.tile.design.TileChrome
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileGauge
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Dedicated Circular Bezel Arc Sleep Tile with Celestial Starry Night Aesthetic & Nocturnal Temperature
 * integration for Samsung Galaxy Watch (Wear OS 5 / 6).
 *
 * Sleep is the only tile in the carousel with a scene, so it keeps it: a
 * starfield backdrop, an untinted three-tone crescent moon, a sun that sinks
 * behind a horizon limb on entrance. Everything else — palette, type scale,
 * bezel gauge, badges and the action pill — comes from the shared design
 * system, so the tile reads as part of the same family instead of a repainted
 * copy of its neighbours.
 *
 * Motion budget (S6/S9): the sunset is this tile's single non-gauge animator,
 * which is why the bezel track is built with `breathing = false`. Total = 2.
 *
 * Content budget (S4): four blocks — header, hero, metrics row, action pill. The
 * nocturnal skin temperature is a badge inside the header, so it costs no block
 * and the metrics row stays at two badges.
 */
class SleepTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(
            buildTile(this, requestParams.deviceConfiguration)
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
        val vitals = RingConnComplicationProvider.getLatestVitals(this)
        if (vitals.sleepScore == 0) {
            // No sleep data after midnight rollover — request phone to wake RingConn and sync
            requestPhoneSync()
        }
        TileService.getUpdater(this).requestUpdate(this.javaClass)
    }

    private fun requestPhoneSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = com.google.android.gms.wearable.Wearable.getNodeClient(this@SleepTileService)
                    .connectedNodes
                    .await()
                for (node in nodes) {
                    com.google.android.gms.wearable.Wearable.getMessageClient(this@SleepTileService)
                        .sendMessage(node.id, "/action/sync_ringconn", byteArrayOf())
                        .await()
                    Log.d("SleepTile", "Requested phone sync from node ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.w("SleepTile", "Failed to request phone sync", e)
            }
        }
    }

    companion object {
        const val RESOURCES_VERSION = "oneui8-sleep-celestial-v16"
        private const val RES_IC_STARFIELD = "ic_tile_starfield"
        private const val RES_IC_MOON = "ic_tile_moon"
        private const val RES_IC_SLEEP = "ic_vitals_sleep"
        private const val RES_IC_TEMP = "ic_comp_temp"
        private const val RES_SUN = "sun_disc"
        private const val RES_HORIZON = "horizon_limb"

        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.SleepDetailActivity"

        // Sunset entrance geometry, in dp relative to the tile centre.
        // X is a static offset into the empty lower-left corner (- the crescent owns the
        // right side), so only Y has to animate: the whole entrance costs one animator.
        // The sun starts ABOVE centre so it actually travels 148dp before it is gone;
        // starting at +44 made it begin already half-occluded and it died in 500ms.
        private const val SUN_OFFSET_X_DP = -40f
        private const val SUN_START_Y_DP = -36f
        private const val SUN_END_Y_DP = 112f

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_IC_STARFIELD,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_tile_starfield)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_IC_MOON,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_tile_moon)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_SUN,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.sun_disc)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_HORIZON,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.horizon_limb)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_IC_SLEEP,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_vitals_sleep)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_IC_TEMP,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_comp_temp)
                                .build()
                        ).build()
                )
                .build()
        }

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context)
        ): TileBuilders.Tile = buildTile(AndroidTileStrings(context), context.packageName, deviceParams, vitals)

        fun buildTile(
            strings: TileStrings,
            packageName: String,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals
        ): TileBuilders.Tile {
            val hasSleepData = vitals.sleepScore > 0
            val score = vitals.sleepScore
            val scoreText = if (hasSleepData) "$score" else "--"

            val effectiveMins = vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins
            val durationText = if (effectiveMins > 0) {
                val h = effectiveMins / 60
                val m = effectiveMins % 60
                if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
            } else if (hasSleepData) {
                "--"
            } else {
                strings.text(R.string.no_data)
            }

            val qualityText = when {
                !hasSleepData -> strings.text(R.string.no_data)
                score >= 85 -> strings.text(R.string.excellent)
                score >= 70 -> strings.text(R.string.good)
                else -> strings.text(R.string.fair)
            }

            // The sleep ramp already carries the three quality states; the no-data
            // tile borrows the calmest end of it instead of inventing a grey.
            val accent = when {
                !hasSleepData -> TileDesign.Accent.sleepLavender
                score >= 85 -> TileDesign.Accent.sleep[0]
                score >= 70 -> TileDesign.Accent.sleep[1]
                else -> TileDesign.Accent.sleep[2]
            }

            // Integrated Nocturnal Skin Temperature reading
            val offset = vitals.skinTempOffset
            val hasTempData = vitals.skinTempBaseline > 0f || vitals.skinTempOffset != 0f
            val baseline = if (vitals.skinTempBaseline > 0f) vitals.skinTempBaseline else 36.1f

            val offsetStr = if (hasTempData) {
                when {
                    offset in -0.05f..0.05f -> "±0.0"
                    offset > 0.05f -> String.format(Locale.US, "+%.1f", offset)
                    else -> String.format(Locale.US, "%.1f", offset)
                }
            } else {
                "--"
            }

            val tempAccent = when {
                !hasTempData -> TileDesign.Text.MUTED
                offset in -0.5f..0.5f -> TileDesign.Accent.stable
                offset > 0.5f -> TileDesign.Accent.warm
                else -> TileDesign.Accent.cool
            }

            val tempDisplayText = if (hasTempData) {
                "$offsetStr°C · ${String.format(Locale.US, "%.1f°", baseline)}"
            } else {
                "--°C · ${strings.text(R.string.no_data)}"
            }

            val fraction = if (hasSleepData) score.coerceIn(0, 100) / 100f else 0f

            val clickable = TileChrome.launchDetail(packageName, DETAIL_ACTIVITY)

            // Block 1 — header: icon + uppercase metric. Nothing else competes with
            // the hero, which now owns a full 40sp line on a 192dp bezel.
            val headerIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_SLEEP)
                .setWidth(dp(15f))
                .setHeight(dp(15f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(argb(TileDesign.Accent.starlight))
                        .build()
                )
                .build()

            val headerLabel = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.sleep_short).uppercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.label(TileDesign.Accent.starlight))
                .build()

            // The nocturnal skin temperature rides in the header as a badge: it is a
            // quiet recovery read next to the metric name, and moving it here keeps
            // the metrics row down to two badges so a longer localized quality word
            // or duration still has margin inside the bezel.
            val tempBadge = TileChrome.badge(
                LayoutElementBuilders.Text.Builder()
                    .setText(tempDisplayText)
                    .setMaxLines(1)
                    .setFontStyle(TileDesign.Type.caption(tempAccent))
                    .build()
            )

            val headerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(headerIcon)
                .addContent(TileChrome.hSpacer(4f))
                .addContent(headerLabel)
                .addContent(TileChrome.hSpacer(5f))
                .addContent(tempBadge)
                .build()

            // Block 2 — hero: the 40sp score with its unit, alone in its row.
            val heroNumber = LayoutElementBuilders.Text.Builder()
                .setText(scoreText)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.hero())
                .build()

            val heroUnit = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.pts_unit).lowercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.unit(TileDesign.Accent.sleepLavender))
                .build()

            val heroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(heroNumber)
                .addContent(TileChrome.hSpacer(3f))
                .addContent(heroUnit)
                .build()

            // Block 3 — metrics row: quality word and duration, two badges only.
            val qualityBadge = TileChrome.badge(
                LayoutElementBuilders.Text.Builder()
                    .setText(qualityText)
                    .setMaxLines(1)
                    .setFontStyle(TileDesign.Type.caption(accent))
                    .build()
            )

            val durationBadge = TileChrome.badge(
                LayoutElementBuilders.Text.Builder()
                    .setText(durationText)
                    .setMaxLines(1)
                    .setFontStyle(TileDesign.Type.caption(TileDesign.Text.PRIMARY))
                    .build()
            )

            val metricsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(qualityBadge)
                .addContent(TileChrome.hSpacer(4f))
                .addContent(durationBadge)
                .build()

            // Block 4 — the single action pill.
            val actionPill = TileChrome.actionPill(
                LayoutElementBuilders.Text.Builder()
                    .setText(strings.text(R.string.details))
                    .setMaxLines(1)
                    .setFontStyle(TileDesign.Type.caption(TileDesign.Text.PRIMARY))
                    .build(),
                clickable
            )

            val contentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(headerRow)
                .addContent(TileChrome.spacer(4f))
                .addContent(heroRow)
                .addContent(TileChrome.spacer(6f))
                .addContent(metricsRow)
                .addContent(TileChrome.spacer(8f))
                .addContent(actionPill)
                .build()

            // ===== The scene: unchanged resources, unchanged geometry =====

            // 1. Starfield backdrop, full-bleed and first: it paints over the metric
            // gradient when opaque, which is the intent.
            val starfieldBackground = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_STARFIELD)
                .setWidth(expand())
                .setHeight(expand())
                .build()

            // 2. Crescent moon, left untinted so its three tones (aura, body,
            // highlight) survive.
            val celestialMoon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_MOON)
                .setWidth(expand())
                .setHeight(expand())
                .build()

            // 3. Setting sun: a soft-edged disc that translates down behind the
            // horizon. Only Y animates; this is the tile's one scene animator.
            val sunsetSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(1700L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.42f, 0.0f, 0.26f, 1.0f))
                        .build()
                )
                .build()

            val sunSink = DimensionBuilders.DpProp.Builder(SUN_START_Y_DP)
                .setDynamicValue(
                    DynamicBuilders.DynamicFloat.animate(
                        SUN_START_Y_DP,
                        SUN_END_Y_DP,
                        sunsetSpec
                    )
                )
                .build()

            val sunImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_SUN)
                .setWidth(dp(70f))
                .setHeight(dp(70f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setTransformation(
                            ModifiersBuilders.Transformation.Builder()
                                .setTranslationX(dp(SUN_OFFSET_X_DP))
                                .setTranslationY(sunSink)
                                .build()
                        )
                        .build()
                )
                .build()

            // 4. Occluder. Drawn after the sun, so plain z-order hides it as it sinks.
            val horizonImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_HORIZON)
                .setWidth(expand())
                .setHeight(expand())
                .build()

            // Bezel gauge: breathing = false because the sunset already spends the
            // tile's second animator (S9).
            val gauge = TileGauge.elements(
                accent = accent,
                targetSweep = TileGauge.sweepFor(fraction),
                breathing = false
            )

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(TileChrome.rootModifiers(TileMetric.SLEEP, clickable))
                .addContent(starfieldBackground)
                .addContent(celestialMoon)
                .addContent(sunImage)
                .addContent(horizonImage)
                .addContent(gauge[0])
                .addContent(gauge[1])
                .addContent(contentColumn)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
