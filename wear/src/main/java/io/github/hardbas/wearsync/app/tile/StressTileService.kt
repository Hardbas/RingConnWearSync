package io.github.hardbas.wearsync.app.tile

import android.content.Context
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

/**
 * Dedicated Circular Bezel Arc Stress Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Ergonomically calibrated for round screens to ensure zero text crowding and maximum glanceability.
 */
class StressTileService : TileService() {

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
    }

    companion object {
        const val RESOURCES_VERSION = "oneui8-stress-circular-v1"
        private const val RES_IC_STRESS = "ic_comp_stress"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_IC_STRESS,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_comp_stress)
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
            val subtitleColor = argb(0xFFD8B4FE.toInt())
            val frostedGlassPill = argb(0x2BFFFFFF.toInt())
            val bezelTrackColor = argb(0x24FFFFFF.toInt())

            val hasStress = vitals.stressScore > 0
            val score = vitals.stressScore.coerceIn(0, 100)
            val scoreStr = if (hasStress) "$score" else "--"

            val statusColorInt = when {
                !hasStress -> 0xFF686D73.toInt()
                score < 30 -> 0xFF00E676.toInt()     // Relaxed (Green)
                score < 60 -> 0xFF00E5FF.toInt()     // Balanced (Cyan)
                score < 80 -> 0xFFFFD600.toInt()     // Moderate (Yellow)
                else -> 0xFFFF3D00.toInt()           // High (Orange-Red)
            }
            val statusColor = argb(statusColorInt)

            val statusPulseColorInt = when {
                !hasStress -> 0xFF686D73.toInt()
                score < 30 -> 0xFF007A3D.toInt()
                score < 60 -> 0xFF007A8A.toInt()
                score < 80 -> 0xFF8A7200.toInt()
                else -> 0xFF8A1A00.toInt()
            }

            val statusText = when {
                !hasStress -> context.getString(R.string.no_data)
                score < 30 -> context.getString(R.string.stress_relaxed)
                score < 60 -> context.getString(R.string.stress_balanced)
                score < 80 -> context.getString(R.string.stress_moderate)
                else -> context.getString(R.string.stress_high)
            }

            val hrvSubtitle = if (vitals.lastHrv > 0) {
                "${context.getString(R.string.hrv_short)} ${vitals.lastHrv} ms"
            } else {
                context.getString(R.string.stress_resting)
            }

            val mainTileAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("io.github.hardbas.wearsync.app.ui.StressDetailActivity")
                        .build()
                ).build()

            val mainTileClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(mainTileAction)
                .build()

            val totalArcSpanDegrees = 260f
            val arcStartAngle = 140f

            val targetSweepDegrees = if (hasStress) {
                val fraction = (score / 100f).coerceIn(0.04f, 1.0f)
                fraction * totalArcSpanDegrees
            } else {
                14f
            }

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(650L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            val dynamicSweep = DimensionBuilders.DegreesProp.Builder(targetSweepDegrees)
                .setDynamicValue(
                    DynamicBuilders.DynamicFloat.animate(
                        AppDataKey<DynamicBuilders.DynamicFloat>("stress_arc_sweep"),
                        sweepAnimSpec
                    )
                )
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
                .setColor(statusColor)
                .build()

            val activeArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(activeArcLine)
                .build()

            // 3. Title: "Estrés" (11.5sp bold white)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.stress_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 4. Subtitle: HRV indicator (10sp)
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText(hrvSubtitle)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(subtitleColor)
                        .build()
                )
                .build()

            val pulseAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(650L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.4f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .setRepeatable(
                    AnimationParameterBuilders.Repeatable.Builder()
                        .setRepeatMode(AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                        .build()
                )
                .build()

            val stressIconTint = if (hasStress) {
                val dynamicColor = DynamicBuilders.DynamicColor.animate(
                    statusColorInt,
                    statusPulseColorInt,
                    pulseAnimSpec
                )
                androidx.wear.protolayout.ColorBuilders.ColorProp.Builder(statusColorInt)
                    .setDynamicValue(dynamicColor)
                    .build()
            } else {
                statusColor
            }

            // 5. Hero Row: Stress Icon + 30sp Bold Score
            val stressIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_STRESS)
                .setWidth(dp(16f))
                .setHeight(dp(16f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(stressIconTint)
                        .build()
                )
                .build()

            val scoreText = LayoutElementBuilders.Text.Builder()
                .setText(scoreStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(30f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val heroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stressIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
                .addContent(scoreText)
                .build()

            // 6. Zone Badge Pill ("Relajado", "Equilibrado", etc.)
            val statusPillText = LayoutElementBuilders.Text.Builder()
                .setText(statusText)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(statusColor)
                        .build()
                )
                .build()

            val statusBadgeBox = LayoutElementBuilders.Box.Builder()
                .setWidth(wrap())
                .setHeight(dp(20f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
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
                        .build()
                )
                .addContent(statusPillText)
                .build()

            // 7. Ergonomic Frosted Glass Action Button ("Respirar")
            val actionButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.breathe))
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
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(heroRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).setWidth(dp(1f)).build())
                .addContent(statusBadgeBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(7f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            // Root Canvas: Full-bleed Circular Box with Bezel Arcs + Center Content
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
                    AppDataKey<DynamicBuilders.DynamicFloat>("stress_arc_sweep"),
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
