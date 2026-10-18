package com.ringconn.wear.app.tile

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
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import com.ringconn.wear.app.ui.TemperatureDetailActivity

/**
 * Dedicated Circular Bezel Arc Skin Temperature Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 * Features:
 * - 260° sweeping perimeter bezel arc gauge mapped to thermoregulation deviation (-1.5°C to +1.5°C)
 * - 38sp Hero digits with tabular numerals
 * - Color-coded deviation status badge (Stable · Optimal, Warm, Cool)
 * - Personal circadian baseline indicator
 * - Ergonomic frosted glass action button ("Detalles")
 * - 100% full-bleed circular layout with zero wasted borders
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
            val offset = vitals.skinTempOffset
            val baseline = if (vitals.skinTempBaseline > 0f) vitals.skinTempBaseline else 36.1f
            val offsetStr = when {
                offset in -0.05f..0.05f -> "±0.0"
                offset > 0.05f -> String.format(java.util.Locale.US, "+%.1f", offset)
                else -> String.format(java.util.Locale.US, "%.1f", offset)
            }

            val statusText = when {
                offset in -0.5f..0.5f -> context.getString(R.string.temp_stable_optimal)
                offset > 0.5f -> context.getString(R.string.temp_warm)
                else -> context.getString(R.string.temp_cool)
            }

            val statusColorInt = when {
                offset in -0.5f..0.5f -> 0xFF00E676.toInt() // Clinical Mint Emerald Optimal
                offset > 0.5f -> 0xFFFF7043.toInt()         // Warm Amber Alert
                else -> 0xFF38BDF8.toInt()                  // Sky Cyan Cooling
            }

            val baselineSubtitle = context.getString(R.string.temp_baseline_short, baseline)

            val whiteColor = argb(0xFFFFFFFF.toInt())
            val unitColor = argb(0xFF34D399.toInt())
            val statusColor = argb(statusColorInt)
            val subtitleColor = argb(0xFFCBD5E1.toInt())        // Slate 300
            val frostedGlassPill = argb(0x26FFFFFF.toInt())     // 15% Frosted glass
            val bezelTrackColor = argb(0x24FFFFFF.toInt())      // Dark frosted bezel track

            val clickAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(TemperatureDetailActivity::class.java.name)
                        .build()
                ).build()

            val mainTileClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(clickAction)
                .setId("open_temperature_detail")
                .build()

            // Dynamic Sweeping Bezel Arc Gauge (260° total span, 140° to 40°)
            val totalArcSpanDegrees = 260f
            val arcStartAngle = 140f

            // Map offset [-1.5°C .. +1.5°C] to sweep degrees
            val clampedFraction = ((offset.coerceIn(-1.5f, 1.5f) + 1.5f) / 3.0f).coerceIn(0.08f, 1.0f)
            val targetSweepDegrees = clampedFraction * totalArcSpanDegrees

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
                        AppDataKey("temp_arc_sweep"),
                        sweepAnimSpec
                    )
                )
                .build()

            val angularConstraint = DimensionBuilders.AngularLayoutConstraint.Builder(totalArcSpanDegrees)
                .build()

            // 1. Outer Bezel Track (Static subtle track)
            val bgTrackArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(DimensionBuilders.degrees(totalArcSpanDegrees))
                .setThickness(dp(7f))
                .setColor(bezelTrackColor)
                .build()

            val bgArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(bgTrackArcLine)
                .build()

            // 2. Active Temperature Bezel Arc (Animated dynamic sweep)
            val activeArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(dynamicSweep)
                .setLayoutConstraintsForDynamicLength(angularConstraint)
                .setThickness(dp(7f))
                .setColor(statusColor)
                .build()

            val activeArc = LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(arcStartAngle))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(activeArcLine)
                .build()

            // 3. Title: "Temp. Cutánea" (12.5sp bold white)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_temperature_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(12.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 4. Subtitle: Baseline indicator
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText(baselineSubtitle)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(subtitleColor)
                        .build()
                )
                .build()

            // 5. Hero Row: Temp Icon + 38sp Bold Number + "°C"
            val tempIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_TEMP)
                .setWidth(dp(20f))
                .setHeight(dp(20f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(statusColor)
                        .build()
                )
                .build()

            val tempNumberText = LayoutElementBuilders.Text.Builder()
                .setText(offsetStr)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(38f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val tempUnitText = LayoutElementBuilders.Text.Builder()
                .setText("°C")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(13f))
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
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).setHeight(dp(1f)).build())
                .addContent(tempNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).setHeight(dp(1f)).build())
                .addContent(tempUnitText)
                .build()

            // 6. Status Badge Pill ("Estable · Óptimo", "Templado", etc.)
            val statusPillText = LayoutElementBuilders.Text.Builder()
                .setText(statusText)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(statusColor)
                        .build()
                )
                .build()

            val statusBadgeBox = LayoutElementBuilders.Box.Builder()
                .setWidth(wrap())
                .setHeight(dp(22f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(0x22FFFFFF.toInt()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(11f)).build())
                                .build()
                        )
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(10f))
                                .setEnd(dp(10f))
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
                        .setSize(sp(12f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val actionPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(96f))
                .setHeight(dp(28f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainTileClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(frostedGlassPill)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(14f)).build())
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
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(5f)).setWidth(dp(1f)).build())
                .addContent(heroTempRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(statusBadgeBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).setWidth(dp(1f)).build())
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
                                .build()
                        )
                        .build()
                )
                .addContent(bgArc)
                .addContent(activeArc)
                .addContent(contentColumn)
                .build()

            val layout = LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(layout)
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            val tileState = StateBuilders.State.Builder()
                .addKeyToValueMapping(AppDataKey("temp_arc_sweep"), DynamicDataBuilders.DynamicDataValue.fromFloat(targetSweepDegrees))
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setState(tileState)
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
