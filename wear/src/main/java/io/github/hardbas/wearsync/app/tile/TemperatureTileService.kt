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
 * Dedicated Circular Bezel Arc Skin Temperature Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Ergonomically calibrated for round screens to ensure zero text crowding and maximum glanceability.
 */
class TemperatureTileService : TileService() {

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
        const val RESOURCES_VERSION = "oneui8-temp-circular-v1"
        private const val RES_IC_TEMP = "ic_comp_temp"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
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
            val subtitleColor = argb(0xFFD1FAE5.toInt())
            val unitColor = argb(0xFF68D391.toInt())
            val frostedGlassPill = argb(0x2BFFFFFF.toInt())
            val bezelTrackColor = argb(0x24FFFFFF.toInt())

            val offset = vitals.skinTempOffset
            val hasData = vitals.skinTempBaseline > 0f || vitals.skinTempOffset != 0f
            val baseline = if (vitals.skinTempBaseline > 0f) vitals.skinTempBaseline else 36.1f

            val offsetStr = if (hasData) {
                when {
                    offset in -0.05f..0.05f -> "±0.0"
                    offset > 0.05f -> String.format(java.util.Locale.US, "+%.1f", offset)
                    else -> String.format(java.util.Locale.US, "%.1f", offset)
                }
            } else {
                "--"
            }

            val statusColorInt = when {
                !hasData -> 0xFF686D73.toInt()
                offset in -0.5f..0.5f -> 0xFF00E676.toInt()   // Optimal Euthermia (Green)
                offset > 0.5f -> 0xFFFF7043.toInt()           // Warm departure (Amber/Coral)
                else -> 0xFF38BDF8.toInt()                    // Cool departure (Cyan)
            }
            val statusColor = argb(statusColorInt)

            val statusPulseColorInt = when {
                !hasData -> 0xFF686D73.toInt()
                offset in -0.5f..0.5f -> 0xFF007A3D.toInt()   // Deeper green pulse
                offset > 0.5f -> 0xFF8A2E12.toInt()           // Deeper coral pulse
                else -> 0xFF026AA7.toInt()                    // Deeper cyan pulse
            }

            val statusText = when {
                !hasData -> context.getString(R.string.no_data)
                offset in -0.5f..0.5f -> context.getString(R.string.temp_in_range)
                offset > 0.5f -> context.getString(R.string.temp_warm)
                else -> context.getString(R.string.temp_cool)
            }

            val baselineSubtitle = if (hasData && vitals.skinTempBaseline > 0f) {
                context.getString(R.string.temp_baseline_short, baseline)
            } else {
                context.getString(R.string.no_data)
            }

            val mainTileAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName("io.github.hardbas.wearsync.app.ui.TemperatureDetailActivity")
                        .build()
                ).build()

            val mainTileClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(mainTileAction)
                .build()

            val totalArcSpanDegrees = 260f
            val arcStartAngle = 140f

            val targetSweepDegrees = if (hasData) {
                val norm = ((offset + 1.0f) / 2.0f).coerceIn(0.04f, 1.0f)
                norm * totalArcSpanDegrees
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
                        AppDataKey<DynamicBuilders.DynamicFloat>("temp_arc_sweep"),
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

            // 3. Title: "Temp. Cutánea" (11sp bold white)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_temperature_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 4. Subtitle: Baseline indicator (10sp)
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText(baselineSubtitle)
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

            val tempIconTint = if (hasData) {
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

            // 5. Hero Row: Temp Icon + 30sp Bold Number + "°C"
            val tempIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_TEMP)
                .setWidth(dp(16f))
                .setHeight(dp(16f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(tempIconTint)
                        .build()
                )
                .build()

            val tempNumberText = LayoutElementBuilders.Text.Builder()
                .setText(offsetStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(30f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val tempUnitText = LayoutElementBuilders.Text.Builder()
                .setText("°C")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(unitColor)
                        .build()
                )
                .build()

            val heroTempRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(tempIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
                .addContent(tempNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(tempUnitText)
                .build()

            // 6. Status Badge Pill ("En rango", "Cálido", etc.)
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

            // 7. Ergonomic Frosted Glass Action Button ("Detalles")
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
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(heroTempRow)
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
                    AppDataKey<DynamicBuilders.DynamicFloat>("temp_arc_sweep"),
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
