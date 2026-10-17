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
 * Dedicated One UI Watch 8 Skin Temperature Tile for Samsung Galaxy Watch 8.
 * Renders the 176dp x 126dp Stadium Capsule layout with baseline offset,
 * fluctuation bar, and localized system strings without runtime layoutConstraint crashes.
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
        const val RESOURCES_VERSION = "oneui8-temp-v9"
        private const val RES_BG_TEMP_CAPSULE = "oneui_bg_temp_capsule"
        private const val RES_IC_TEMP = "ic_comp_temp"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_BG_TEMP_CAPSULE,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_bg_temp_capsule)
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

            val emeraldAccentColor = 0xFF34D399.toInt()    // Luminous Mint Emerald
            val labelMintColor = 0xFFD1FAE5.toInt()        // Soft Mint White

            val statusColorInt = when {
                offset in -0.5f..0.5f -> 0xFF00E676.toInt() // Vibrant clinical mint optimal
                offset > 0.5f -> 0xFFFF7043.toInt()         // Warm amber alert
                else -> 0xFF38BDF8.toInt()                  // Sky cyan cooling
            }

            val baselineSubtitle = context.getString(R.string.temp_baseline_short, baseline)

            val whiteColor = argb(0xFFFFFFFF.toInt())
            val silverColor = argb(labelMintColor)
            val unitColor = argb(emeraldAccentColor)
            val statusColor = argb(statusColorInt)
            val translucentGlassBlack = argb(0x66000000.toInt())

            val clickAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(TemperatureDetailActivity::class.java.name)
                        .build()
                ).build()

            val mainCapsuleClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(clickAction)
                .setId("open_temperature_detail")
                .build()

            // 1. One UI Watch 8 Enlarged Gentle AI Ruby Stadium Capsule (206dp x 146dp)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_TEMP_CAPSULE)
                .setWidth(dp(206f))
                .setHeight(dp(146f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Title Text (13sp Medium Pastel Rose)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_temperature_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(silverColor)
                        .build()
                )
                .build()

            // 3. Subtitle Row (Status · Baseline)
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText("$statusText  ·  $baselineSubtitle")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(statusColor)
                        .build()
                )
                .build()

            // 4. Hero Offset Row (Icon + Big Value + °C)
            val tempIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_TEMP)
                .setWidth(dp(18f))
                .setHeight(dp(18f))
                .build()

            val offsetNumberText = LayoutElementBuilders.Text.Builder()
                .setText(offsetStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.HERO))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val unitText = LayoutElementBuilders.Text.Builder()
                .setText("°C")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
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
                .addContent(offsetNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(unitText)
                .build()

            // 5. One UI 8 Dynamic Animated Fluctuation Scale Bar (-1.0°C to +1.0°C, 146dp x 10dp)
            val barWidthDp = 146f
            val barHeightDp = 10f
            val normOffset = ((offset + 1.0f) / 2.0f).coerceIn(0f, 1f)
            val dotPosDp = ((normOffset * barWidthDp) - 4f).coerceIn(0f, barWidthDp - 8f)
            val baselinePosDp = (barWidthDp * 0.5f) - 4f

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(600L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Continuous pulse animation for indicator dot (800ms reverse loop, reverse-engineered from shealth cg7.java:482)
            val pulseAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(800L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.4f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .setRepeatable(
                    AnimationParameterBuilders.Repeatable.Builder()
                        .setRepeatMode(AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                        .build()
                )
                .build()

            val dynamicDotSpacerWidth = DimensionBuilders.DpProp.Builder(dotPosDp)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(AppDataKey("temp_dot_pos"), sweepAnimSpec))
                .build()

            val dynamicDotSize = DimensionBuilders.DpProp.Builder(8f)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(6.5f, 9.5f, pulseAnimSpec))
                .build()

            val barTrackBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(5f)).build())
                                .build()
                        ).build()
                )
                .build()

            // Optimal middle indicator range (-0.5°C to +0.5°C, 25% to 75% width)
            val optZoneBar = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp * 0.5f))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(0x4400E676.toInt()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(3f)).build())
                                .build()
                        ).build()
                )
                .build()

            val optZoneRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(barWidthDp * 0.25f)).setHeight(dp(1f)).build())
                .addContent(optZoneBar)
                .build()

            // Center Reference Tick at 0.0°C baseline
            val centerTick = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(1.5f))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(0x88FFFFFF.toInt()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(0.75f)).build())
                                .build()
                        ).build()
                )
                .build()

            val centerTickRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp((barWidthDp * 0.5f) - 0.75f)).setHeight(dp(1f)).build())
                .addContent(centerTick)
                .build()

            val dynamicDotColor = DynamicBuilders.DynamicColor.animate(
                0xFFFFFFFF.toInt(),
                0x33FFFFFF.toInt(),
                pulseAnimSpec
            )

            val dynamicDotTint = androidx.wear.protolayout.ColorBuilders.ColorProp.Builder(0xFFFFFFFF.toInt())
                .setDynamicValue(dynamicDotColor)
                .build()

            val dotIndicator = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(8f))
                .setHeight(dp(8f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(dynamicDotTint)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(4f)).build())
                                .build()
                        ).build()
                )
                .build()

            val dotIndicatorRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicDotSpacerWidth)
                        .setHeight(dp(barHeightDp))
                        .build()
                )
                .addContent(dotIndicator)
                .build()

            val tempChartBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(barTrackBox)
                .addContent(optZoneRow)
                .addContent(centerTickRow)
                .addContent(dotIndicatorRow)
                .build()

            // 6. Action Pill Button (94dp x 24dp, radius 12dp, translucent glass)
            val actionButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.details))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val actionPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(94f))
                .setHeight(dp(24f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(12f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(actionButtonText)
                .build()

            // Assemble inside the 206dp x 146dp Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(206f))
                .setHeight(dp(146f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).setWidth(dp(1f)).build())
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(heroTempRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(tempChartBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(206f))
                .setHeight(dp(146f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .build()
                        )
                        .build()
                )
                .addContent(bgImage)
                .addContent(capsuleContentColumn)
                .build()

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stadiumCapsuleBox)
                .build()

            val layout = LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(layout)
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            val tileState = StateBuilders.State.Builder()
                .addKeyToValueMapping(AppDataKey("temp_dot_pos"), DynamicDataBuilders.DynamicDataValue.fromFloat(dotPosDp))
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
