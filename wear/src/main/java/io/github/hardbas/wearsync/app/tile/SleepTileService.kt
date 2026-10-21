package io.github.hardbas.wearsync.app.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TriggerBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicDataBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Dedicated Circular Bezel Arc Sleep Tile with Celestial Starry Night Aesthetic & Nocturnal Temperature
 * integration for Samsung Galaxy Watch (Wear OS 5 / 6).
 *
 * Combines:
 * 1. Deep obsidian twilight starfield backdrop (ic_tile_starfield).
 * 2. Animated sleep score bezel sweep arc.
 * 3. Hero sleep score & quality badge.
 * 4. Sleep duration indicator.
 * 5. Integrated Nocturnal Skin Temperature recovery capsule.
 * 6. Glanceable "Detalles" launcher for SleepDetailActivity.
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
        const val RESOURCES_VERSION = "oneui8-sleep-celestial-v8"
        private const val RES_IC_STARFIELD = "ic_tile_starfield"
        private const val RES_IC_MOON_AVD = "avd_tile_moon_rise"
        private const val RES_IC_MOON = "ic_tile_moon"
        private const val RES_IC_SLEEP = "ic_vitals_sleep"
        private const val RES_IC_TEMP = "ic_comp_temp"

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
                    RES_IC_MOON_AVD,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidAnimatedResourceByResId(
                            ResourceBuilders.AndroidAnimatedImageResourceByResId.Builder()
                                .setResourceId(R.drawable.avd_tile_moon_rise)
                                .setAnimatedImageFormat(ResourceBuilders.ANIMATED_IMAGE_FORMAT_AVD)
                                .setStartTrigger(TriggerBuilders.createOnVisibleTrigger())
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
        ): TileBuilders.Tile {
            val whiteColor = argb(0xFFFFFFFF.toInt())
            val sleepLavender = argb(0xFFA5AEDE.toInt())
            val starlightGold = argb(0xFFFFD166.toInt())
            val frostedGlassPill = argb(0x2BFFFFFF.toInt())
            val frostedPillSubtle = argb(0x1EFFFFFF.toInt())
            val bezelTrackColor = argb(0x24FFFFFF.toInt())

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
                context.getString(R.string.no_data)
            }

            val qualityText = when {
                !hasSleepData -> context.getString(R.string.no_data)
                score >= 85 -> context.getString(R.string.excellent)
                score >= 70 -> context.getString(R.string.good)
                else -> context.getString(R.string.fair)
            }

            val arcColorInt = when {
                !hasSleepData -> 0xFF5E72E4.toInt()
                score >= 85 -> 0xFF47CDFF.toInt()   // Electric Cyan
                score >= 70 -> 0xFF5E72E4.toInt()   // Royal Indigo
                else -> 0xFFFFD166.toInt()          // Starlight Gold / Amber
            }
            val arcColor = argb(arcColorInt)

            // Integrated Nocturnal Skin Temperature calculation
            val offset = vitals.skinTempOffset
            val hasTempData = vitals.skinTempBaseline > 0f || vitals.skinTempOffset != 0f
            val baseline = if (vitals.skinTempBaseline > 0f) vitals.skinTempBaseline else 36.1f

            val offsetStr = if (hasTempData) {
                when {
                    offset in -0.05f..0.05f -> "±0.0"
                    offset > 0.05f -> String.format(java.util.Locale.US, "+%.1f", offset)
                    else -> String.format(java.util.Locale.US, "%.1f", offset)
                }
            } else {
                "--"
            }

            val tempStatusColorInt = when {
                !hasTempData -> 0xFF8A9099.toInt()
                offset in -0.5f..0.5f -> 0xFF00E676.toInt()   // Optimal Euthermia (Green)
                offset > 0.5f -> 0xFFFF7043.toInt()           // Warm departure (Amber)
                else -> 0xFF38BDF8.toInt()                    // Cool departure (Cyan)
            }
            val tempStatusColor = argb(tempStatusColorInt)

            val tempDisplayText = if (hasTempData) {
                "$offsetStr°C · ${String.format(java.util.Locale.US, "%.1f°", baseline)}"
            } else {
                "--°C · ${context.getString(R.string.no_data)}"
            }

            val mainTileAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("io.github.hardbas.wearsync.app.ui.SleepDetailActivity")
                        .build()
                ).build()

            val mainTileClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(mainTileAction)
                .build()

            // Circular Bezel Gauge (260 degrees arc span)
            val totalArcSpanDegrees = 260f
            val arcStartAngle = 140f

            val targetSweepDegrees = if (hasSleepData) {
                ((score.coerceIn(0, 100) / 100f) * totalArcSpanDegrees).coerceAtLeast(14f)
            } else {
                14f
            }

            // Samsung One UI 8 dynamic entrance sweep (0° -> target in 900ms, cubicBezier curve)
            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(900L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.0f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .build()

            val dynamicSweep = DimensionBuilders.DegreesProp.Builder(targetSweepDegrees)
                .setDynamicValue(
                    DynamicBuilders.DynamicFloat.animate(
                        0f,
                        targetSweepDegrees,
                        sweepAnimSpec
                    )
                )
                .build()

            // Smooth celestial moonlight breathing glow (1500ms reverse loop, Samsung cg7.java parity)
            val moonGlowAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(1500L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.4f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .setRepeatable(
                    AnimationParameterBuilders.Repeatable.Builder()
                        .setRepeatMode(AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                        .build()
                )
                .build()

            val dynamicMoonColor = DynamicBuilders.DynamicColor.animate(
                0xFF5E72E4.toInt(), // Deep celestial twilight indigo
                0xFFFFD166.toInt(), // Warm radiant starlight gold
                moonGlowAnimSpec
            )
            val dynamicMoonProp = androidx.wear.protolayout.ColorBuilders.ColorProp.Builder(0xFFFFD166.toInt())
                .setDynamicValue(dynamicMoonColor)
                .build()

            val angularConstraint = DimensionBuilders.AngularLayoutConstraint.Builder(totalArcSpanDegrees)
                .build()

            val bgTrackArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(DimensionBuilders.degrees(totalArcSpanDegrees))
                .setThickness(dp(6.5f))
                .setColor(bezelTrackColor)
                .build()

            val bgArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(bgTrackArcLine)
                .build()

            val activeArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(dynamicSweep)
                .setLayoutConstraintsForDynamicLength(angularConstraint)
                .setThickness(dp(6.5f))
                .setColor(arcColor)
                .build()

            val activeArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(activeArcLine)
                .build()

            // 1. Starfield Background Image & Celestial Moon Backdrop
            val starfieldBackground = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_STARFIELD)
                .setWidth(expand())
                .setHeight(expand())
                .build()

            // Hero 115dp Celestial Moon Backdrop: Pulsing with living celestial starlight glow
            val celestialMoon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_MOON)
                .setWidth(dp(115f))
                .setHeight(dp(115f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(dynamicMoonProp)
                        .build()
                )
                .build()

            // 2. Header Row: Solitary Pulsating Crescent Moon + "SUEÑO" / "SLEEP"
            val sleepIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_SLEEP)
                .setWidth(dp(15f))
                .setHeight(dp(15f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(starlightGold)
                        .build()
                )
                .build()

            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.sleep_short).uppercase())
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(starlightGold)
                        .build()
                )
                .build()

            val headerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(sleepIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(titleText)
                .build()

            // 3. Hero Row: Sleep Score (32sp) + "pts" (11sp)
            val scoreNumberText = LayoutElementBuilders.Text.Builder()
                .setText(scoreText)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(32f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val ptsUnitText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.pts_unit).uppercase())
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(sleepLavender)
                        .build()
                )
                .build()

            val heroScoreRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                .addContent(scoreNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(ptsUnitText)
                .build()

            // 4. Quality Pill + Sleep Duration Badge
            val qualityBadgeText = LayoutElementBuilders.Text.Builder()
                .setText(qualityText.uppercase())
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(arcColor)
                        .build()
                )
                .build()

            val durationBadgeText = LayoutElementBuilders.Text.Builder()
                .setText(durationText)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val metricsRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(wrap())
                        .setHeight(dp(18f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(frostedPillSubtle)
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(9f)).build())
                                        .build()
                                )
                                .setPadding(
                                    ModifiersBuilders.Padding.Builder()
                                        .setStart(dp(7f))
                                        .setEnd(dp(7f))
                                        .build()
                                )
                                .build()
                        )
                        .addContent(qualityBadgeText)
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(wrap())
                        .setHeight(dp(18f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(frostedPillSubtle)
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(9f)).build())
                                        .build()
                                )
                                .setPadding(
                                    ModifiersBuilders.Padding.Builder()
                                        .setStart(dp(7f))
                                        .setEnd(dp(7f))
                                        .build()
                                )
                                .build()
                        )
                        .addContent(durationBadgeText)
                        .build()
                )
                .build()

            // 5. Integrated Nocturnal Temperature Recovery Pill (Direct Row container to prevent unmeasurable drop)
            val tempIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_TEMP)
                .setWidth(dp(11f))
                .setHeight(dp(11f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(tempStatusColor)
                        .build()
                )
                .build()

            val tempText = LayoutElementBuilders.Text.Builder()
                .setText(tempDisplayText)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val tempCapsuleRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(dp(20f))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(0x22FFFFFF.toInt()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(10f)).build())
                                .build()
                        )
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(8f))
                                .setEnd(dp(8f))
                                .build()
                        )
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .build()
                        )
                        .build()
                )
                .addContent(tempIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(tempText)
                .build()

            // 6. Ergonomic Frosted Glass Action Button ("Detalles")
            val actionButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.details))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val actionPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(88f))
                .setHeight(dp(26f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainTileClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(frostedGlassPill)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(13f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(actionButtonText)
                .build()

            // Center Column Content
            val contentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .build()
                        )
                        .build()
                )
                .addContent(headerRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(1f)).setWidth(dp(1f)).build())
                .addContent(heroScoreRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(metricsRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(tempCapsuleRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            // Root Canvas: Full-bleed Box with Starfield, Celestial Moon, Bezel Arcs, and Content
            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainTileClickable)
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .setExitTransition(ModifiersBuilders.DefaultContentTransitions.fadeOut())
                                .build()
                        )
                        .build()
                )
                .addContent(starfieldBackground)
                .addContent(celestialMoon)
                .addContent(bgArc)
                .addContent(activeArc)
                .addContent(contentColumn)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            val tileState = StateBuilders.State.Builder()
                .addKeyToValueMapping(
                    AppDataKey<DynamicBuilders.DynamicFloat>("sleep_arc_sweep"),
                    DynamicDataBuilders.DynamicDataValue.fromFloat(targetSweepDegrees)
                )
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setState(tileState)
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
