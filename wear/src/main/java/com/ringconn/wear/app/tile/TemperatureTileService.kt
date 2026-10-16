package com.ringconn.wear.app.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
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

    companion object {
        const val RESOURCES_VERSION = "oneui8-temp-v6"
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

            val limeStepsColor = 0xFFA0F116.toInt()       // Neon Lime (from Daily Activity)
            val cyanAccentColor = 0xFF47CDFF.toInt()      // Electric Sky Cyan (from Daily Activity)
            val labelSilverColor = 0xFFB7B7BB.toInt()     // Metallic Titanium Silver

            val statusColorInt = when {
                offset in -0.5f..0.5f -> limeStepsColor // Vibrant neon lime optimal!
                offset > 0.5f -> 0xFFFF9800.toInt()     // Vivid warm amber
                else -> cyanAccentColor                 // Electric sky cyan
            }

            val baselineSubtitle = context.getString(R.string.temp_baseline_short, baseline)

            val whiteColor = argb(0xFFFFFFFF.toInt())
            val silverColor = argb(labelSilverColor)
            val cyanUnitColor = argb(cyanAccentColor)
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

            // 1. One UI Watch 8 Circular Fit Stadium Capsule (190dp x 136dp)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_TEMP_CAPSULE)
                .setWidth(dp(190f))
                .setHeight(dp(136f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Title Text (13sp Medium Silver)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.tile_temperature_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(13f))
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
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(statusColor)
                        .build()
                )
                .build()

            // 4. Hero Offset Row (Icon + Big Value + °C)
            val tempIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_TEMP)
                .setWidth(dp(16f))
                .setHeight(dp(16f))
                .build()

            val offsetNumberText = LayoutElementBuilders.Text.Builder()
                .setText(offsetStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(26f))
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
                        .setSize(sp(13f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(cyanUnitColor)
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

            // 5. One UI 8 Static Deterministic Fluctuation Scale Bar (-1.0°C to +1.0°C, 136dp x 10dp)
            val barWidthDp = 136f
            val barHeightDp = 10f
            val normOffset = ((offset + 1.0f) / 2.0f).coerceIn(0f, 1f)
            val dotPosDp = ((normOffset * barWidthDp) - 3.5f).coerceIn(0f, barWidthDp - 7f)

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
                                .setColor(argb(0x55A0F116.toInt()))
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
                                .setColor(argb(0x66FFFFFF.toInt()))
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

            val dotIndicator = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(7f))
                .setHeight(dp(7f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(whiteColor)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(3.5f)).build())
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
                        .setWidth(dp(dotPosDp))
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

            // 6. Action Pill Button (92dp x 24dp, radius 12dp, 50% glass)
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
                .setWidth(dp(92f))
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

            // Assemble inside the 190dp x 136dp Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(190f))
                .setHeight(dp(136f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).setWidth(dp(1f)).build())
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(heroTempRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(tempChartBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(7f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(190f))
                .setHeight(dp(136f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
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

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
